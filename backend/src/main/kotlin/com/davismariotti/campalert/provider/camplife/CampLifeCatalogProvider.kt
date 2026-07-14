package com.davismariotti.campalert.provider.camplife

import com.davismariotti.campalert.api.model.AmenityOption
import com.davismariotti.campalert.api.model.CampgroundResponse
import com.davismariotti.campalert.api.model.CampgroundSearchResult
import com.davismariotti.campalert.api.model.CampsiteResponse
import com.davismariotti.campalert.api.model.LoopInfo
import com.davismariotti.campalert.delegate.toApi
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.service.availability.CampgroundCatalogProvider
import org.springframework.stereotype.Service

/** CampLife's catalog strategy: served entirely from the cached global directory/campground catalog (design.md decision 7) — CampLife itself has no server-side search, and its own frontend does the same client-side substring matching after one fetch. */
@Service
class CampLifeCatalogProvider(
    private val campLifeCatalogCache: CampLifeCatalogCache,
) : CampgroundCatalogProvider {
    override val provider = Provider.CAMPLIFE

    override fun search(query: String): List<CampgroundSearchResult> {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return emptyList()
        val resultProvider = provider.toApi()
        return campLifeCatalogCache
            .getDirectory()
            .filter { entry ->
                entry.name.lowercase().contains(needle) ||
                    entry.city?.lowercase()?.contains(needle) == true ||
                    entry.stateProvince?.lowercase()?.contains(needle) == true
            }.map { entry -> CampgroundSearchResult(id = entry.id, name = entry.name, provider = resultProvider) }
    }

    /**
     * Builds the [CampgroundResponse] shape from CampLife's cached catalog alone — one cheap cached
     * call, no per-site fan-out. Per-site amenities are deliberately NOT included here: CampLife
     * exposes no reliable way to know a site's amenities without asking its own availability
     * endpoint, which is what request-level `amenityIds` filtering (matched via `isFiltered`) is for
     * instead of resolving it in this catalog listing.
     */
    override fun getCampground(id: Int): CampgroundResponse? {
        val catalog = campLifeCatalogCache.getCampgroundCatalog(id) ?: return null
        val equipTypeNamesById = catalog.config.equipTypes.associate { it.id to it.name }
        val equipTypeIdsByGrouping = catalog.config.siteTypes.associate { it.name to it.equipTypeIds }

        val campsites = catalog.siteMap.entries.associate { (siteId, site) ->
            val equipmentTypeNames = (equipTypeIdsByGrouping[site.typeName] ?: emptyList()).mapNotNull { equipTypeNamesById[it] }
            siteId to site.toCampsiteResponse(equipmentTypeNames)
        }

        return CampgroundResponse(
            campsites = campsites,
            equipmentTypes = catalog.config.equipTypes
                .map { it.name }
                .takeIf { it.isNotEmpty() },
            amenities = catalog.config.amenities
                .map { AmenityOption(id = it.id, name = it.name) }
                .takeIf { it.isNotEmpty() },
        )
    }

    override fun getLoops(id: Int): List<LoopInfo> {
        val catalog = campLifeCatalogCache.getCampgroundCatalog(id) ?: return emptyList()
        return catalog.config.siteTypes
            .map { it.name }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .map { LoopInfo(name = it, boatInOnly = false) }
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
}
