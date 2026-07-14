package com.davismariotti.campalert.provider.recreation

import com.davismariotti.campalert.api.model.CampgroundSearchResult
import com.davismariotti.campalert.delegate.toApi
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.service.availability.CampgroundCatalogSearchProvider
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** Recreation.gov's catalog search strategy: a live RIDB facility search, unchanged from before cross-provider search existed. */
@Service
class RecreationCatalogSearchProvider(
    private val ridbApi: RidbApi,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
) : CampgroundCatalogSearchProvider {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ridbCb by lazy { circuitBreakerRegistry.circuitBreaker("ridb") }

    override val provider = Provider.RECREATION_GOV

    /** Returns an empty list when RIDB's circuit is open; propagates other failures for the caller to handle (see [com.davismariotti.campalert.delegate.CampgroundsDelegateImpl]). */
    override fun search(query: String): List<CampgroundSearchResult> {
        val response = try {
            ridbCb.executeSupplier { ridbApi.getFacilities(query).execute() }
        } catch (e: CallNotPermittedException) {
            log.warn("RIDB circuit open for searchCampgrounds q={}", query)
            return emptyList()
        }
        if (!response.isSuccessful) {
            error("RIDB upstream error searchCampgrounds q=$query code=${response.code()}")
        }
        val resultProvider = provider.toApi()
        return response
            .body()
            ?.recdata
            ?.filter { it.facilityTypeDescription == "Campground" }
            ?.mapNotNull { facility ->
                facility.facilityId.toIntOrNull()?.let { id ->
                    CampgroundSearchResult(id = id, name = facility.facilityName, provider = resultProvider)
                }
            }
            ?: emptyList()
    }
}
