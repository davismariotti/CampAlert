package com.davismariotti.campalert.provider.reservecalifornia

import com.davismariotti.campalert.provider.CallProtection
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.service.redis.RedisJsonCache
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import retrofit2.Call
import retrofit2.Response
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers a real production bug (see `ReserveCaliforniaCatalogCache.refreshDirectory`'s own doc
 * comment): Retrofit returns a null body for any non-2xx response instead of throwing, so a
 * transient failure from `getPlaces`/`getFacilities` was previously indistinguishable from a
 * legitimately empty result and got cached as the whole directory for a full TTL with nothing
 * logged as an error.
 */
class ReserveCaliforniaCatalogCacheTest {
    private val reserveCaliforniaApi = mock(ReserveCaliforniaApi::class.java)
    private val redisJsonCache = mock(RedisJsonCache::class.java)
    private val callProtection: CallProtection =
        CallProtection
            .Builder(Provider.RESERVE_CALIFORNIA)
            .circuitBreaker(CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()))
            .retry(RetryRegistry.of(RetryConfig.ofDefaults()))
            .build()
    private val properties = ReserveCaliforniaCatalogProperties(ttlDays = 7)

    private val cache = ReserveCaliforniaCatalogCache(reserveCaliforniaApi, redisJsonCache, callProtection, properties)

    private val place = ReserveCaliforniaPlace(placeId = 614, name = "Angel Island SP")
    private val facility = ReserveCaliforniaFacility(facilityId = 407, placeId = 614, name = "West Garrison")

    private fun mockPlaces(places: List<ReserveCaliforniaPlace>) {
        @Suppress("UNCHECKED_CAST")
        val call = mock(Call::class.java) as Call<List<ReserveCaliforniaPlace>>
        `when`(call.execute()).thenReturn(Response.success(places))
        `when`(reserveCaliforniaApi.getPlaces()).thenReturn(call)
    }

    private fun mockPlacesError() {
        @Suppress("UNCHECKED_CAST")
        val call = mock(Call::class.java) as Call<List<ReserveCaliforniaPlace>>
        `when`(call.execute()).thenReturn(Response.error(403, "blocked".toResponseBody("text/plain".toMediaType())))
        `when`(reserveCaliforniaApi.getPlaces()).thenReturn(call)
    }

    private fun mockFacilities(facilities: List<ReserveCaliforniaFacility>) {
        @Suppress("UNCHECKED_CAST")
        val call = mock(Call::class.java) as Call<List<ReserveCaliforniaFacility>>
        `when`(call.execute()).thenReturn(Response.success(facilities))
        `when`(reserveCaliforniaApi.getFacilities()).thenReturn(call)
    }

    @Test
    fun `a non-2xx response from getPlaces is treated as a failed call, not zero places`() {
        mockPlacesError()
        mockFacilities(listOf(facility))

        val result = cache.refreshDirectory()

        assertNull(result)
        verify(redisJsonCache, never()).set(anyString(), any(), any(), any())
    }

    @Test
    fun `a join that produces zero entries is treated as a failed call, not cached`() {
        mockPlaces(emptyList())
        mockFacilities(listOf(facility))

        val result = cache.refreshDirectory()

        assertNull(result)
        verify(redisJsonCache, never()).set(anyString(), any(), any(), any())
    }

    @Test
    fun `a successful join with matching entries is cached`() {
        mockPlaces(listOf(place))
        mockFacilities(listOf(facility))

        val result = cache.refreshDirectory()

        assertEquals(listOf(407), result?.map { it.facilityId })
        verify(redisJsonCache).set(anyString(), any(), any(), any())
    }

    @Test
    fun `isDirectoryRefreshDue is true when the cached directory is empty regardless of age`() {
        `when`(redisJsonCache.get(anyString(), anyOrNull<tools.jackson.core.type.TypeReference<Any>>()))
            .thenReturn(ReserveCaliforniaCachedEntry(Instant.now(), emptyList<ReserveCaliforniaDirectoryEntry>()))

        assertTrue(cache.isDirectoryRefreshDue(intervalMs = Long.MAX_VALUE))
    }

    @Test
    fun `getDirectory self-heals a previously poisoned empty cache once the underlying call succeeds`() {
        `when`(redisJsonCache.get(anyString(), anyOrNull<tools.jackson.core.type.TypeReference<Any>>()))
            .thenReturn(ReserveCaliforniaCachedEntry(Instant.now(), emptyList<ReserveCaliforniaDirectoryEntry>()))
        mockPlaces(listOf(place))
        mockFacilities(listOf(facility))

        val result = cache.getDirectory()

        assertEquals(listOf(407), result.map { it.facilityId })
        verify(redisJsonCache).set(anyString(), any(), any(), any())
    }
}
