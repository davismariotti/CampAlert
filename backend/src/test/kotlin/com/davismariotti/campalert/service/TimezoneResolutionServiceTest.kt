package com.davismariotti.campalert.service

import com.davismariotti.campalert.camplife.CampLifeCatalogCache
import com.davismariotti.campalert.camplife.CampLifeDirectoryEntry
import com.davismariotti.campalert.model.Provider
import com.davismariotti.campalert.recreation.RidbApi
import com.davismariotti.campalert.recreation.RidbFacility
import com.davismariotti.campalert.recreation.RidbFacilityResponse
import com.davismariotti.campalert.repository.SearchRequestRepository
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import net.iakovlev.timeshape.TimeZoneEngine
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import retrofit2.Call
import retrofit2.Response
import java.util.Optional

class TimezoneResolutionServiceTest {
    private val ridbApi = mock(RidbApi::class.java)
    private val campLifeCatalogCache = mock(CampLifeCatalogCache::class.java)
    private val timeZoneEngine = mock(TimeZoneEngine::class.java)
    private val searchRequestRepository = mock(SearchRequestRepository::class.java)

    private val circuitBreakerRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())

    private val service = TimezoneResolutionService(
        ridbApi,
        campLifeCatalogCache,
        timeZoneEngine,
        searchRequestRepository,
        circuitBreakerRegistry,
    )

    @BeforeEach
    fun resetCircuitBreaker() {
        circuitBreakerRegistry.circuitBreaker("ridb").reset()
    }

    @Test
    fun `successful resolution persists timezone`() {
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
        `when`(
            timeZoneEngine.query(37.8716, -122.2727)
        ).thenReturn(Optional.of(java.time.ZoneId.of("America/Los_Angeles")))

        service.resolveAndPersistAsync(1L, 10, Provider.RECREATION_GOV)

        verify(searchRequestRepository).updateTimezone(1L, "America/Los_Angeles")
    }

    @Test
    fun `RIDB exception logs warn and does not call repository`() {
        val call = mock(Call::class.java)
        @Suppress("UNCHECKED_CAST")
        `when`(ridbApi.getFacility(10)).thenReturn(call as Call<RidbFacilityResponse>)
        `when`(call.execute()).thenThrow(RuntimeException("RIDB down"))

        service.resolveAndPersistAsync(1L, 10, Provider.RECREATION_GOV)

        verify(searchRequestRepository, never()).updateTimezone(anyLong(), org.mockito.Mockito.any())
    }

    @Test
    fun `circuit open logs warn and does not call repository`() {
        circuitBreakerRegistry.circuitBreaker("ridb").transitionToOpenState()

        service.resolveAndPersistAsync(1L, 10, Provider.RECREATION_GOV)

        verify(searchRequestRepository, never()).updateTimezone(anyLong(), org.mockito.Mockito.any())
        verify(ridbApi, never()).getFacility(anyInt())
    }

    @Test
    fun `CampLife request resolves timezone from cached directory entry without calling RIDB`() {
        `when`(campLifeCatalogCache.getDirectory()).thenReturn(
            listOf(CampLifeDirectoryEntry(id = 791, name = "Collins Lake", lat = "39.3374", lon = "-121.1544")),
        )
        `when`(timeZoneEngine.query(39.3374, -121.1544)).thenReturn(Optional.of(java.time.ZoneId.of("America/Los_Angeles")))

        service.resolveAndPersistAsync(1L, 791, Provider.CAMPLIFE)

        verify(searchRequestRepository).updateTimezone(1L, "America/Los_Angeles")
        verify(ridbApi, never()).getFacility(anyInt())
    }

    @Test
    fun `CampLife request with no matching directory entry persists null timezone`() {
        `when`(campLifeCatalogCache.getDirectory()).thenReturn(emptyList())

        service.resolveAndPersistAsync(1L, 791, Provider.CAMPLIFE)

        verify(searchRequestRepository).updateTimezone(1L, null)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> mockCall(body: T): Call<T> {
        val call = mock(Call::class.java) as Call<T>
        `when`(call.execute()).thenReturn(Response.success(body))
        return call
    }
}
