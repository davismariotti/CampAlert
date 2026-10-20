package com.davismariotti.campalert.service.availability

import com.davismariotti.campalert.api.model.CampgroundResponse
import com.davismariotti.campalert.api.model.CampgroundSearchResult
import com.davismariotti.campalert.api.model.LoopInfo
import com.davismariotti.campalert.provider.Provider

/**
 * A provider's own catalog strategy (design.md decision 7 / catalog-search-provider-scoping) —
 * Recreation.gov queries RIDB live; other providers may use whatever mechanism suits the catalog
 * data they expose (e.g. CampLife's cached-directory substring match). Each implementation owns its
 * own failure handling; callers merging across providers should treat a thrown exception as "this
 * provider contributed no results" rather than failing the whole search.
 */
interface CampgroundCatalogProvider {
    val provider: Provider

    /**
     * Whether this provider's results are merged into an unscoped (no `provider` parameter) campground
     * search. Providers that aren't campgrounds (e.g. Global Entry enrollment centers) opt out so they
     * only appear when searched for explicitly via their own provider-scoped search.
     */
    val includeInUnscopedSearch: Boolean get() = true

    fun search(query: String): List<CampgroundSearchResult>

    fun getCampground(id: Int): CampgroundResponse?

    fun getLoops(id: Int): List<LoopInfo>
}
