package com.davismariotti.campalert.service

import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.service.availability.CampgroundCoordinateProviderRegistry
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import net.iakovlev.timeshape.TimeZoneEngine
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class TimezoneResolutionService(
    private val coordinateProviderRegistry: CampgroundCoordinateProviderRegistry,
    private val timeZoneEngine: TimeZoneEngine,
    private val searchRequestRepository: SearchRequestRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** [campsiteId] is actually the campground/facility id here (Recreation.gov's own confusing terminology — see design.md decision 4's naming note); its coordinate source depends on [provider]. */
    @Async("timezoneResolutionExecutor")
    fun resolveAndPersistAsync(searchRequestId: Long, campsiteId: Int, provider: Provider) {
        try {
            val (lat, lon) = coordinateProviderRegistry.forProvider(provider).resolveCoordinates(campsiteId)
            val timezone = if (lat != null && lon != null) {
                timeZoneEngine.query(lat, lon).map { it.id }.orElse(null)
            } else {
                null
            }
            searchRequestRepository.updateTimezone(searchRequestId, timezone)
        } catch (e: CallNotPermittedException) {
            log.warn("Circuit open for timezone resolution provider={} requestId={} campsiteId={}", provider, searchRequestId, campsiteId)
        } catch (e: Exception) {
            log.warn("Failed to resolve timezone provider={} requestId={} campsiteId={}", provider, searchRequestId, campsiteId, e)
        }
    }
}
