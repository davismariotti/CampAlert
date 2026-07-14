package com.davismariotti.campalert.provider.camplife

import com.davismariotti.campalert.provider.CallProtection
import com.davismariotti.campalert.service.redis.RedisJsonCache
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import retrofit2.Call
import retrofit2.Response
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CampLifeCatalogCacheTest {
    private val campLifeApi = mock(CampLifeApi::class.java)
    private val redisJsonCache = mock(RedisJsonCache::class.java)
    private val callProtection: CallProtection =
        CallProtection
            .Builder("camplife")
            .circuitBreaker(CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()))
            .retry(RetryRegistry.of(RetryConfig.ofDefaults()))
            .build()
    private val properties = CampLifeCatalogProperties(
        ttlDays = 7,
        staleAfterDays = 3
    )
    private val executor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 4
        maxPoolSize = 4
        initialize()
    }

    private val cache = CampLifeCatalogCache(
        campLifeApi,
        redisJsonCache,
        callProtection,
        properties,
        executor
    )

    private fun mockDirectoryCall(entries: List<CampLifeDirectoryEntry>, latch: CountDownLatch? = null) {
        @Suppress("UNCHECKED_CAST")
        val call = mock(Call::class.java) as Call<List<CampLifeDirectoryEntry>>
        `when`(call.execute()).thenAnswer {
            latch?.countDown()
            Response.success(entries)
        }
        `when`(campLifeApi.getDirectory(any(), any())).thenReturn(call)
    }

    @Test
    fun `cold cache fetches and stores the directory`() {
        mockDirectoryCall(
            listOf(
                CampLifeDirectoryEntry(
                    id = 1,
                    name = "A"
                )
            )
        )
        `when`(redisJsonCache.get(any<String>(), any<tools.jackson.core.type.TypeReference<CampLifeCachedEntry<List<CampLifeDirectoryEntry>>>>())).thenReturn(null)

        val result = cache.getDirectory()

        assertEquals(1, result.size)
        verify(redisJsonCache).set(any(), any(), org.mockito.kotlin.eq(7L), org.mockito.kotlin.eq(TimeUnit.DAYS))
    }

    @Test
    fun `fresh cache is served without triggering a refresh`() {
        mockDirectoryCall(
            listOf(
                CampLifeDirectoryEntry(
                    id = 1,
                    name = "A"
                )
            )
        )
        val fresh = CampLifeCachedEntry(
            Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS),
            listOf(
                CampLifeDirectoryEntry(
                    id = 2,
                    name = "cached"
                )
            )
        )
        `when`(redisJsonCache.get(any<String>(), any<tools.jackson.core.type.TypeReference<CampLifeCachedEntry<List<CampLifeDirectoryEntry>>>>())).thenReturn(fresh)

        val result = cache.getDirectory()
        executor.threadPoolExecutor.awaitTermination(200, TimeUnit.MILLISECONDS)

        assertEquals(
            listOf(
                CampLifeDirectoryEntry(
                    id = 2,
                    name = "cached"
                )
            ),
            result
        )
        verify(campLifeApi, never()).getDirectory(any(), any())
    }

    @Test
    fun `stale cache is served immediately and triggers exactly one background refresh under concurrent reads`() {
        val fetchLatch = CountDownLatch(1)
        mockDirectoryCall(
            listOf(
                CampLifeDirectoryEntry(
                    id = 1,
                    name = "refreshed"
                )
            ),
            fetchLatch
        )
        val stale = CampLifeCachedEntry(
            Instant.now().minus(4, java.time.temporal.ChronoUnit.DAYS),
            listOf(
                CampLifeDirectoryEntry(
                    id = 2,
                    name = "stale-cached"
                )
            )
        )
        `when`(redisJsonCache.get(any<String>(), any<tools.jackson.core.type.TypeReference<CampLifeCachedEntry<List<CampLifeDirectoryEntry>>>>())).thenReturn(stale)

        val guard = AtomicLong(0)
        `when`(redisJsonCache.increment(any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenAnswer { guard.incrementAndGet() }

        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(5)
        val results = (1..5).map {
            Thread {
                startLatch.await()
                results@ run { cache.getDirectory() }
                doneLatch.countDown()
            }.also { it.start() }
        }
        startLatch.countDown()
        assertTrue(doneLatch.await(2, TimeUnit.SECONDS))
        assertTrue(fetchLatch.await(2, TimeUnit.SECONDS), "expected exactly one background refresh to run")
        results.forEach { it.join() }

        verify(campLifeApi, times(1)).getDirectory(any(), any())
    }

    @Test
    fun `failed refresh leaves the prior cached value in place`() {
        @Suppress("UNCHECKED_CAST")
        val call = mock(Call::class.java) as Call<List<CampLifeDirectoryEntry>>
        `when`(call.execute()).thenThrow(RuntimeException("network error"))
        `when`(campLifeApi.getDirectory(any(), any())).thenReturn(call)

        val result = cache.refreshDirectory()

        assertEquals(null, result)
        verify(redisJsonCache, never()).set(any(), any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }
}
