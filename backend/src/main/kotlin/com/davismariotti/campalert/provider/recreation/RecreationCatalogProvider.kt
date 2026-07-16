package com.davismariotti.campalert.provider.recreation

import com.davismariotti.campalert.api.model.CampgroundResponse
import com.davismariotti.campalert.api.model.CampgroundSearchResult
import com.davismariotti.campalert.api.model.CampsiteResponse
import com.davismariotti.campalert.api.model.LoopInfo
import com.davismariotti.campalert.delegate.toApi
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.service.availability.CampgroundCatalogProvider
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter

/** Recreation.gov's catalog strategy: RIDB facility search/campsites, unchanged from before cross-provider search existed. */
@Service
class RecreationCatalogProvider(
    private val recreationApi: RecreationApi,
    private val ridbApi: RidbApi,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
) : CampgroundCatalogProvider {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ridbCb by lazy { circuitBreakerRegistry.circuitBreaker("ridb") }

    override val provider = Provider.RECREATION_GOV

    /** Returns an empty list when RIDB's circuit is open; propagates other failures for the caller to handle (see [com.davismariotti.campalert.delegate.CampgroundsDelegateImpl]). */
    override fun search(query: String): List<CampgroundSearchResult> {
        val response = try {
            ridbCb.executeSupplier { ridbApi.getFacilities(query).execute() }
        } catch (e: CallNotPermittedException) {
            log.warn("RIDB circuit open for searchCampgrounds q={}", query, e)
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

    override fun getCampground(id: Int): CampgroundResponse? {
        val campground = recreationApi.getCampgroundAvailability(id).execute().body() ?: return null
        return CampgroundResponse(
            campsites = campground.campsites.entries.associate { (campsiteId, campsite) ->
                campsiteId.toString() to CampsiteResponse(
                    campsiteId = campsite.campsiteId,
                    site = campsite.site,
                    loop = campsite.loop,
                    campsiteReserveType = campsite.campsiteReserveType,
                    minimumNumberOfPeople = campsite.minimumNumberOfPeople,
                    maximumNumberOfPeople = campsite.maximumNumberOfPeople,
                    availabilities = campsite.availabilities.entries.associate { (dt, type) ->
                        dt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) to type.toValue()
                    },
                    quantities = campsite.quantities.entries.associate { (dt, qty) ->
                        dt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) to qty
                    },
                )
            },
        )
    }

    /** Returns an empty list when RIDB's circuit is open; propagates other failures for the caller to handle (see [com.davismariotti.campalert.delegate.CampgroundsDelegateImpl]). */
    override fun getLoops(id: Int): List<LoopInfo> {
        val response = try {
            ridbCb.executeSupplier { ridbApi.getCampsites(id).execute() }
        } catch (e: CallNotPermittedException) {
            log.warn("RIDB circuit open for getCampgroundLoops id={}", id, e)
            return emptyList()
        }
        if (!response.isSuccessful) {
            error("RIDB upstream error getCampgroundLoops id=$id code=${response.code()}")
        }
        val bySites = response
            .body()
            ?.recdata
            ?.filter { !it.loop.isNullOrBlank() }
            ?.groupBy { it.loop!! }
            ?: emptyMap()
        return bySites.entries
            .map { (loop, sites) ->
                LoopInfo(
                    name = loop,
                    boatInOnly = sites.all { it.campsiteType?.uppercase() == "BOAT IN" } ||
                        loop.uppercase().contains("BOAT"),
                )
            }.sortedBy { it.name }
    }
}
