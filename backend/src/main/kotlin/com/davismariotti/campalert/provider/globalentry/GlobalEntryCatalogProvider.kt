package com.davismariotti.campalert.provider.globalentry

import com.davismariotti.campalert.api.model.CampgroundResponse
import com.davismariotti.campalert.api.model.CampgroundSearchResult
import com.davismariotti.campalert.api.model.LoopInfo
import com.davismariotti.campalert.delegate.toApi
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.service.availability.CampgroundCatalogProvider
import org.springframework.stereotype.Service

/**
 * Global Entry's catalog strategy: client-side matching over the cached enrollment center directory
 * (the scheduler has no search endpoint, and there are only ~135 centers). Centers have no campsites,
 * loops, equipment types, or amenities, so those catalog operations return empty shapes. Opts out of
 * unscoped campground search — it's reached through its own provider-scoped search.
 */
@Service
class GlobalEntryCatalogProvider(
    private val globalEntryCatalogCache: GlobalEntryCatalogCache,
) : CampgroundCatalogProvider {
    override val provider = Provider.GLOBAL_ENTRY

    override val includeInUnscopedSearch = false

    /**
     * Matches the query against the state code (case-insensitive equality) and, for queries of
     * [MIN_TEXT_QUERY_LENGTH]+ characters, name, short name, or city (case-insensitive substring). A
     * 1-2 character query is a state code lookup only: substring-matching it would make "CA" return
     * California centers plus anything with "ca" in its name ("Chicago").
     */
    override fun search(query: String): List<CampgroundSearchResult> {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return emptyList()
        val matchesText = needle.length >= MIN_TEXT_QUERY_LENGTH
        val resultProvider = provider.toApi()
        return globalEntryCatalogCache
            .getDirectory()
            .filter { location ->
                location.state?.lowercase() == needle ||
                    (
                        matchesText &&
                            (
                                location.name.lowercase().contains(needle) ||
                                    location.shortName?.lowercase()?.contains(needle) == true ||
                                    location.city?.lowercase()?.contains(needle) == true
                            )
                    )
            }.map { location -> CampgroundSearchResult(id = location.id, name = location.displayName(), provider = resultProvider) }
    }

    override fun getCampground(id: Int): CampgroundResponse? {
        globalEntryCatalogCache.findLocation(id) ?: return null
        return CampgroundResponse(campsites = emptyMap(), equipmentTypes = null, amenities = null)
    }

    override fun getLoops(id: Int): List<LoopInfo> = emptyList()

    private companion object {
        const val MIN_TEXT_QUERY_LENGTH = 3
    }
}
