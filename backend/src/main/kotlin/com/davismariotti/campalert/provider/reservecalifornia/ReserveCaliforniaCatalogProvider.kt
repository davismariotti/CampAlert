package com.davismariotti.campalert.provider.reservecalifornia

import com.davismariotti.campalert.api.model.CampgroundResponse
import com.davismariotti.campalert.api.model.CampgroundSearchResult
import com.davismariotti.campalert.api.model.CampsiteResponse
import com.davismariotti.campalert.api.model.LoopInfo
import com.davismariotti.campalert.delegate.toApi
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.service.availability.CampgroundCatalogProvider
import org.springframework.stereotype.Service

/**
 * ReserveCalifornia's catalog strategy (design.md D2-D6): search matches a locally-cached
 * Places+Facilities directory (ReserveCalifornia's own live search only matches park names, not
 * facility names — see ReserveCaliforniaCatalogCache), never a live call per search. Deliberately
 * does NOT filter results by facility type (D3) — there's no reliable signal distinguishing
 * individual campgrounds from group/picnic/day-use facilities in ReserveCalifornia's metadata, and
 * ReserveCalifornia's own site doesn't hide them either. Do not "fix" this by adding a name-based
 * filter without re-reading design.md D3 first.
 */
@Service
class ReserveCaliforniaCatalogProvider(
    private val catalogCache: ReserveCaliforniaCatalogCache,
) : CampgroundCatalogProvider {
    override val provider = Provider.RESERVE_CALIFORNIA

    override fun search(query: String): List<CampgroundSearchResult> {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return emptyList()
        val resultProvider = provider.toApi()
        return catalogCache
            .getDirectory()
            .filter { entry ->
                entry.facilityName.lowercase().contains(needle) || entry.placeName.lowercase().contains(needle)
            }.map { entry ->
                CampgroundSearchResult(
                    id = entry.facilityId,
                    name = "${entry.facilityName} (${entry.placeName})",
                    provider = resultProvider,
                )
            }
    }

    /**
     * Occupancy (min/max people) is deliberately not surfaced here — ReserveCalifornia's roster has
     * no occupancy field at all (only the per-unit details endpoint does, design.md D9), and eagerly
     * resolving it for a whole facility just to render a catalog view would undermine the
     * demand-driven occupancy warm-up pipeline (D13) and is explicitly out of scope (see design.md
     * Non-Goals). `minimumNumberOfPeople`/`maximumNumberOfPeople` are left unconstrained, matching
     * CampLifeCatalogProvider's own precedent for a provider with no catalog-level occupancy data.
     */
    override fun getCampground(id: Int): CampgroundResponse? {
        val roster = catalogCache.getFacilityRoster(id) ?: return null
        val loopNamesByGroupId = loopNamesByGroupId(id)
        val campsites = roster.units.associate { unit ->
            unit.unitId.toString() to CampsiteResponse(
                campsiteId = unit.unitId,
                site = unit.name,
                loop = unit.unitTypeGroupId?.let { loopNamesByGroupId[it] } ?: "",
                campsiteReserveType = "",
                minimumNumberOfPeople = 0,
                maximumNumberOfPeople = Int.MAX_VALUE,
                availabilities = emptyMap(),
                quantities = emptyMap(),
            )
        }
        return CampgroundResponse(campsites = campsites)
    }

    override fun getLoops(id: Int): List<LoopInfo> {
        val roster = catalogCache.getFacilityRoster(id) ?: return emptyList()
        val loopNamesByGroupId = loopNamesByGroupId(id)
        return roster.units
            .mapNotNull { it.unitTypeGroupId }
            .distinct()
            .mapNotNull { loopNamesByGroupId[it] }
            .filter { it.isNotBlank() }
            .sorted()
            .map { LoopInfo(name = it, boatInOnly = false) }
    }

    private fun loopNamesByGroupId(facilityId: Int): Map<Int, String> {
        val placeId = catalogCache.getDirectory().find { it.facilityId == facilityId }?.placeId ?: return emptyMap()
        return catalogCache
            .getParkFilters(placeId)
            ?.unitTypesGroups
            ?.associate { it.unitTypesGroupId to it.unitTypesGroupName }
            ?: emptyMap()
    }
}
