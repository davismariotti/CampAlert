package com.davismariotti.campalert.service

import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.service.availability.CampgroundCoordinateProvider
import com.davismariotti.campalert.service.availability.CampgroundCoordinateProviderRegistry
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import net.iakovlev.timeshape.TimeZoneEngine
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.Optional

class TimezoneResolutionServiceTest {
    private val timeZoneEngine = mock(TimeZoneEngine::class.java)
    private val searchRequestRepository = mock(SearchRequestRepository::class.java)
    private val coordinateProvider = mock(CampgroundCoordinateProvider::class.java).also {
        `when`(it.provider).thenReturn(Provider.RECREATION_GOV)
    }
    private val service = TimezoneResolutionService(
        CampgroundCoordinateProviderRegistry(listOf(coordinateProvider)),
        timeZoneEngine,
        searchRequestRepository,
    )

    @Test
    fun `successful resolution persists timezone`() {
        `when`(coordinateProvider.resolveCoordinates(10)).thenReturn(37.8716 to -122.2727)
        `when`(timeZoneEngine.query(37.8716, -122.2727)).thenReturn(Optional.of(java.time.ZoneId.of("America/Los_Angeles")))

        service.resolveAndPersistAsync(1L, 10, Provider.RECREATION_GOV)

        verify(searchRequestRepository).updateTimezone(1L, "America/Los_Angeles")
    }

    @Test
    fun `missing coordinates persists a null timezone`() {
        `when`(coordinateProvider.resolveCoordinates(10)).thenReturn(null to null)

        service.resolveAndPersistAsync(1L, 10, Provider.RECREATION_GOV)

        verify(searchRequestRepository).updateTimezone(1L, null)
    }

    @Test
    fun `provider exception logs warn and does not call repository`() {
        doThrow(RuntimeException("upstream down")).`when`(coordinateProvider).resolveCoordinates(10)

        service.resolveAndPersistAsync(1L, 10, Provider.RECREATION_GOV)

        verify(searchRequestRepository, never()).updateTimezone(anyLong(), org.mockito.Mockito.any())
    }

    @Test
    fun `tripped circuit breaker logs warn and does not call repository`() {
        doThrow(CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("test")))
            .`when`(coordinateProvider)
            .resolveCoordinates(10)

        service.resolveAndPersistAsync(1L, 10, Provider.RECREATION_GOV)

        verify(searchRequestRepository, never()).updateTimezone(anyLong(), org.mockito.Mockito.any())
    }
}
