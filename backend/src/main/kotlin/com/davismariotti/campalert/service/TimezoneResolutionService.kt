package com.davismariotti.campalert.service

import com.davismariotti.campalert.camplife.CampLifeCatalogCache
import com.davismariotti.campalert.model.Provider
import com.davismariotti.campalert.recreation.RidbApi
import com.davismariotti.campalert.repository.SearchRequestRepository
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import net.iakovlev.timeshape.TimeZoneEngine
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class TimezoneResolutionService(
    private val ridbApi: RidbApi,
    private val campLifeCatalogCache: CampLifeCatalogCache,
    private val timeZoneEngine: TimeZoneEngine,
    private val searchRequestRepository: SearchRequestRepository,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ridbCb by lazy { circuitBreakerRegistry.circuitBreaker("ridb") }

    /** [campsiteId] is actually the campground/facility id here (Recreation.gov's own confusing terminology — see design.md decision 4's naming note); its coordinate source depends on [provider]. */
    @Async("timezoneResolutionExecutor")
    fun resolveAndPersistAsync(searchRequestId: Long, campsiteId: Int, provider: Provider) {
        try {
            val (lat, lon) = when (provider) {
                Provider.RECREATION_GOV -> resolveRidbCoordinates(campsiteId)
                Provider.CAMPLIFE -> resolveCampLifeCoordinates(campsiteId)
            }
            val timezone = if (lat != null && lon != null) {
                timeZoneEngine.query(lat, lon).map { it.id }.orElse(null)
            } else {
                null
            }
            searchRequestRepository.updateTimezone(searchRequestId, timezone)
        } catch (e: CallNotPermittedException) {
            log.warn("RIDB circuit open for timezone resolution requestId={} campsiteId={}", searchRequestId, campsiteId)
        } catch (e: Exception) {
            log.warn("Failed to resolve timezone for requestId={} campsiteId={}", searchRequestId, campsiteId, e)
        }
    }

    private fun resolveRidbCoordinates(campsiteId: Int): Pair<Double?, Double?> {
        val facilityResponse = ridbCb.executeSupplier { ridbApi.getFacility(campsiteId).execute() }
        val facility = facilityResponse.body()?.recdata
        val lat = facility?.facilityLatitude?.takeIf { it != 0.0 }
        val lon = facility?.facilityLongitude?.takeIf { it != 0.0 }
        return lat to lon
    }

    private fun resolveCampLifeCoordinates(campgroundId: Int): Pair<Double?, Double?> {
        // CampLife's directory serves lat/lon as JSON strings, not numbers (verified against real traffic).
        val entry = campLifeCatalogCache.getDirectory().find { it.id == campgroundId }
        return entry?.lat?.toDoubleOrNull() to entry?.lon?.toDoubleOrNull()
    }
}
