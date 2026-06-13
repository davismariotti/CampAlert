package com.davismariotti.campalert.service

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
    private val timeZoneEngine: TimeZoneEngine,
    private val searchRequestRepository: SearchRequestRepository,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ridbCb by lazy { circuitBreakerRegistry.circuitBreaker("ridb") }

    @Async("timezoneResolutionExecutor")
    fun resolveAndPersistAsync(searchRequestId: Int, campsiteId: Int) {
        try {
            val facilityResponse = ridbCb.executeSupplier { ridbApi.getFacility(campsiteId).execute() }
            val facility = facilityResponse.body()?.recdata
            val lat = facility?.facilityLatitude?.takeIf { it != 0.0 }
            val lon = facility?.facilityLongitude?.takeIf { it != 0.0 }
            val timezone = if (lat != null && lon != null) {
                timeZoneEngine.query(lat, lon).map { it.id }.orElse(null)
            } else {
                null
            }
            searchRequestRepository.updateTimezone(searchRequestId, timezone)
        } catch (e: CallNotPermittedException) {
            log.warn("RIDB circuit open for timezone resolution requestId=$searchRequestId campsiteId=$campsiteId")
        } catch (e: Exception) {
            log.warn("Failed to resolve timezone for requestId=$searchRequestId campsiteId=$campsiteId: ${e.message}")
        }
    }
}
