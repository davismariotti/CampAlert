package com.davismariotti.campalert.provider.globalentry

import com.davismariotti.campalert.provider.CallProtection
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.service.redis.RedisJsonCache
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import retrofit2.Call
import retrofit2.Response
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlobalEntryCatalogCacheTest {
    private val api = mock(GlobalEntryApi::class.java)
    private val redisJsonCache = mock(RedisJsonCache::class.java)
    private val callProtection: CallProtection =
        CallProtection
            .Builder(Provider.GLOBAL_ENTRY)
            .circuitBreaker(CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()))
            .retry(RetryRegistry.of(RetryConfig.ofDefaults()))
            .build()
    private val executor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 2
        maxPoolSize = 2
        initialize()
    }
    private val cache = GlobalEntryCatalogCache(api, redisJsonCache, callProtection, GlobalEntryCatalogProperties(ttlDays = 7, staleAfterDays = 3), executor)

    private fun stubLocations(locations: List<GlobalEntryLocation>) {
        @Suppress("UNCHECKED_CAST")
        val call = mock(Call::class.java) as Call<List<GlobalEntryLocation>>
        `when`(call.execute()).thenReturn(Response.success(locations))
        `when`(api.getLocations(any(), any(), any(), any())).thenReturn(call)
    }

    private fun stubCached(entry: GlobalEntryCachedEntry<List<GlobalEntryLocation>>?) {
        `when`(redisJsonCache.get(any<String>(), any<tools.jackson.core.type.TypeReference<GlobalEntryCachedEntry<List<GlobalEntryLocation>>>>())).thenReturn(entry)
    }

    @Test
    fun `cold cache fetches the directory, drops remote locations, and stores it under the versioned key`() {
        stubCached(null)
        stubLocations(
            listOf(
                GlobalEntryLocation(1, "In person", city = "A", state = "CA", tzData = "America/Los_Angeles"),
                GlobalEntryLocation(2, "Virtual", remoteInd = true),
            ),
        )

        val result = cache.getDirectory()

        assertEquals(listOf(1), result.map { it.id })
        val keyCaptor = argumentCaptor<String>()
        verify(redisJsonCache).set(keyCaptor.capture(), any(), org.mockito.kotlin.eq(7L), org.mockito.kotlin.eq(TimeUnit.DAYS))
        assertEquals("global-entry:catalog:directory:v1", keyCaptor.firstValue)
    }

    @Test
    fun `fresh cache is served without calling the API`() {
        stubCached(GlobalEntryCachedEntry(Instant.now().minusSeconds(3600), listOf(GlobalEntryLocation(9, "Cached"))))

        val result = cache.getDirectory()

        assertEquals(listOf(9), result.map { it.id })
        verify(api, never()).getLocations(any(), any(), any(), any())
    }

    @Test
    fun `findLocation looks the center up in the directory`() {
        stubCached(GlobalEntryCachedEntry(Instant.now(), listOf(GlobalEntryLocation(5446, "SF"), GlobalEntryLocation(1, "Other"))))

        assertEquals("SF", cache.findLocation(5446)?.name)
        assertTrue(cache.findLocation(404) == null)
    }

    @Test
    fun `a failed refresh leaves the previous cached value untouched`() {
        stubCached(null)
        `when`(api.getLocations(any(), any(), any(), any())).thenThrow(RuntimeException("boom"))

        val result = cache.getDirectory()

        assertTrue(result.isEmpty())
        verify(redisJsonCache, never()).set(any(), any(), org.mockito.kotlin.any<Long>(), any())
    }

    @Test
    fun `refresh is due when the cache is empty or older than the interval`() {
        stubCached(null)
        assertTrue(cache.isDirectoryRefreshDue(1000))

        stubCached(GlobalEntryCachedEntry(Instant.now(), emptyList()))
        assertTrue(!cache.isDirectoryRefreshDue(60_000))
    }
}
