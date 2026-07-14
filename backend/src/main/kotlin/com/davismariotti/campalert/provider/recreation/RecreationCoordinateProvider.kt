package com.davismariotti.campalert.provider.recreation

import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.service.availability.CampgroundCoordinateProvider
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.springframework.stereotype.Service

/** Recreation.gov's coordinate source: a live RIDB facility lookup, behind the shared "ridb" circuit breaker. */
@Service
class RecreationCoordinateProvider(
    private val ridbApi: RidbApi,
    circuitBreakerRegistry: CircuitBreakerRegistry,
) : CampgroundCoordinateProvider {
    private val ridbCb by lazy { circuitBreakerRegistry.circuitBreaker("ridb") }

    override val provider = Provider.RECREATION_GOV

    override fun resolveCoordinates(campsiteId: Int): Pair<Double?, Double?> {
        val facilityResponse = ridbCb.executeSupplier { ridbApi.getFacility(campsiteId).execute() }
        val facility = facilityResponse.body()?.recdata
        val lat = facility?.facilityLatitude?.takeIf { it != 0.0 }
        val lon = facility?.facilityLongitude?.takeIf { it != 0.0 }
        return lat to lon
    }
}
