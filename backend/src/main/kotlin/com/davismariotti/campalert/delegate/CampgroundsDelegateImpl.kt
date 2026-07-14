package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.CampgroundsApiDelegate
import com.davismariotti.campalert.api.model.AmenityOption
import com.davismariotti.campalert.api.model.CampgroundResponse
import com.davismariotti.campalert.api.model.CampgroundSearchResult
import com.davismariotti.campalert.api.model.CampsiteResponse
import com.davismariotti.campalert.api.model.LoopInfo
import com.davismariotti.campalert.api.model.ProviderType
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.provider.camplife.CampLifeCatalogCache
import com.davismariotti.campalert.provider.camplife.CampLifeSite
import com.davismariotti.campalert.provider.recreation.RecreationApi
import com.davismariotti.campalert.provider.recreation.RidbApi
import com.davismariotti.campalert.service.availability.CampgroundCatalogSearchProviderRegistry
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.format.DateTimeFormatter

@Service
class CampgroundsDelegateImpl(
    private val recreationApi: RecreationApi,
    private val ridbApi: RidbApi,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val campgroundCatalogSearchProviderRegistry: CampgroundCatalogSearchProviderRegistry,
    private val campLifeCatalogCache: CampLifeCatalogCache,
) : CampgroundsApiDelegate {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ridbCb by lazy { circuitBreakerRegistry.circuitBreaker("ridb") }

    @PreAuthorize("isAuthenticated()")
    override fun getCampground(id: Int, provider: ProviderType?): ResponseEntity<CampgroundResponse> =
        when (provider?.toModel() ?: Provider.RECREATION_GOV) {
            Provider.RECREATION_GOV -> getRecreationGovCampground(id)
            Provider.CAMPLIFE -> getCampLifeCampground(id)
        }

    private fun getRecreationGovCampground(id: Int): ResponseEntity<CampgroundResponse> {
        val campground = recreationApi.getCampgroundAvailability(id).execute().body()
            ?: return ResponseEntity.notFound().build()

        val response = CampgroundResponse(
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
        return ResponseEntity.ok(response)
    }

    /**
     * Builds the [CampgroundResponse] shape from CampLife's cached catalog alone — one cheap cached
     * call, no per-site fan-out. Per-site amenities are deliberately NOT included here: CampLife
     * exposes no reliable way to know a site's amenities without asking its own availability
     * endpoint, which is what request-level `amenityIds` filtering (matched via `isFiltered`) is for
     * instead of resolving it in this catalog listing.
     */
    private fun getCampLifeCampground(id: Int): ResponseEntity<CampgroundResponse> {
        val catalog = campLifeCatalogCache.getCampgroundCatalog(id)
            ?: return ResponseEntity.notFound().build()
        val equipTypeNamesById = catalog.config.equipTypes.associate { it.id to it.name }
        val equipTypeIdsByGrouping = catalog.config.siteTypes.associate { it.name to it.equipTypeIds }

        val campsites = catalog.siteMap.entries.associate { (siteId, site) ->
            val equipmentTypeNames = (equipTypeIdsByGrouping[site.typeName] ?: emptyList()).mapNotNull { equipTypeNamesById[it] }
            siteId to site.toCampsiteResponse(equipmentTypeNames)
        }

        return ResponseEntity.ok(
            CampgroundResponse(
                campsites = campsites,
                equipmentTypes = catalog.config.equipTypes
                    .map { it.name }
                    .takeIf { it.isNotEmpty() },
                amenities = catalog.config.amenities
                    .map { AmenityOption(id = it.id, name = it.name) }
                    .takeIf { it.isNotEmpty() },
            ),
        )
    }

    private fun CampLifeSite.toCampsiteResponse(equipmentTypeNames: List<String>): CampsiteResponse =
        CampsiteResponse(
            campsiteId = this.id,
            site = this.name,
            loop = this.typeName ?: "",
            campsiteReserveType = "",
            // CampLife exposes no per-site minimum-occupancy field.
            minimumNumberOfPeople = 0,
            maximumNumberOfPeople = this.maxOccupants ?: Int.MAX_VALUE,
            availabilities = emptyMap(),
            quantities = emptyMap(),
            equipmentTypes = equipmentTypeNames.takeIf { it.isNotEmpty() },
        )

    @PreAuthorize("isAuthenticated()")
    override fun getCampgroundLoops(id: Int, provider: ProviderType?): ResponseEntity<List<LoopInfo>> =
        when (provider?.toModel() ?: Provider.RECREATION_GOV) {
            Provider.RECREATION_GOV -> getRecreationGovLoops(id)
            Provider.CAMPLIFE -> getCampLifeGroupings(id)
        }

    private fun getRecreationGovLoops(id: Int): ResponseEntity<List<LoopInfo>> {
        val response = try {
            ridbCb.executeSupplier { ridbApi.getCampsites(id).execute() }
        } catch (e: CallNotPermittedException) {
            log.warn("RIDB circuit open for getCampgroundLoops id={}", id)
            return ResponseEntity.ok(emptyList())
        } catch (ex: Exception) {
            log.warn("RIDB error for getCampgroundLoops id={}", id, ex)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "RIDB upstream error")
        }
        if (!response.isSuccessful) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "RIDB upstream error")
        }
        val bySites = response
            .body()
            ?.recdata
            ?.filter { !it.loop.isNullOrBlank() }
            ?.groupBy { it.loop!! }
            ?: emptyMap()
        val loops = bySites.entries
            .map { (loop, sites) ->
                LoopInfo(
                    name = loop,
                    boatInOnly = sites.all { it.campsiteType?.uppercase() == "BOAT IN" } ||
                        loop.uppercase().contains("BOAT"),
                )
            }.sortedBy { it.name }
        return ResponseEntity.ok(loops)
    }

    private fun getCampLifeGroupings(id: Int): ResponseEntity<List<LoopInfo>> {
        val catalog = campLifeCatalogCache.getCampgroundCatalog(id) ?: return ResponseEntity.ok(emptyList())
        val groupings = catalog.config.siteTypes
            .map { it.name }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .map { LoopInfo(name = it, boatInOnly = false) }
        return ResponseEntity.ok(groupings)
    }

    @PreAuthorize("isAuthenticated()")
    override fun searchCampgrounds(q: String, provider: ProviderType?): ResponseEntity<List<CampgroundSearchResult>> {
        if (q.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Query parameter 'q' must not be blank")
        }

        if (provider != null) {
            val results = try {
                campgroundCatalogSearchProviderRegistry.forProvider(provider.toModel()).search(q)
            } catch (ex: Exception) {
                log.warn("Catalog search error provider={} q={}", provider, q, ex)
                throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upstream error")
            }
            return ResponseEntity.ok(results)
        }

        // Unscoped: merge every registered provider's results; one provider failing doesn't blank the others.
        val results = campgroundCatalogSearchProviderRegistry.all().flatMap { searchProvider ->
            try {
                searchProvider.search(q)
            } catch (ex: Exception) {
                log.warn("Catalog search error provider={} q={}", searchProvider.provider, q, ex)
                emptyList()
            }
        }
        return ResponseEntity.ok(results)
    }
}
