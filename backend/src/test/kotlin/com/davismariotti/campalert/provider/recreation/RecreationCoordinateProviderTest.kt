package com.davismariotti.campalert.provider.recreation

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import retrofit2.Call
import retrofit2.Response
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RecreationCoordinateProviderTest {
    private val ridbApi = mock(RidbApi::class.java)
    private val circuitBreakerRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
    private val provider = RecreationCoordinateProvider(ridbApi, circuitBreakerRegistry)

    @BeforeEach
    fun resetCircuitBreaker() {
        circuitBreakerRegistry.circuitBreaker("ridb").reset()
    }

    @Test
    fun `resolves lat lon from the RIDB facility response`() {
        val facility = RidbFacility(
            facilityId = "10",
            facilityName = "Test",
            facilityTypeDescription = "Campground",
            parentRecAreaId = null,
            facilityLatitude = 37.8716,
            facilityLongitude = -122.2727,
        )
        val call = mockCall(RidbFacilityResponse(recdata = facility))
        `when`(ridbApi.getFacility(10)).thenReturn(call)

        assertEquals(37.8716 to -122.2727, provider.resolveCoordinates(10))
    }

    @Test
    fun `propagates the RIDB call's exception`() {
        val call = mock(Call::class.java)
        @Suppress("UNCHECKED_CAST")
        `when`(ridbApi.getFacility(10)).thenReturn(call as Call<RidbFacilityResponse>)
        `when`(call.execute()).thenThrow(RuntimeException("RIDB down"))

        assertFailsWith<RuntimeException> { provider.resolveCoordinates(10) }
    }

    @Test
    fun `propagates a tripped circuit breaker without calling RIDB`() {
        circuitBreakerRegistry.circuitBreaker("ridb").transitionToOpenState()

        assertFailsWith<CallNotPermittedException> { provider.resolveCoordinates(10) }
        verify(ridbApi, never()).getFacility(10)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> mockCall(body: T): Call<T> {
        val call = mock(Call::class.java) as Call<T>
        `when`(call.execute()).thenReturn(Response.success(body))
        return call
    }
}
