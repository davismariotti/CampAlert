package com.davismariotti.campalert.service.availability

import com.davismariotti.campalert.api.model.CampgroundSearchResult
import com.davismariotti.campalert.model.Provider

/**
 * A provider's own catalog search strategy (design.md decision 7 / catalog-search-provider-scoping)
 * — Recreation.gov queries RIDB live; other providers may use whatever mechanism suits the catalog
 * data they expose (e.g. CampLife's cached-directory substring match). Each implementation owns its
 * own failure handling; callers merging across providers should treat a thrown exception as "this
 * provider contributed no results" rather than failing the whole search.
 */
interface CampgroundCatalogSearchProvider {
    val provider: Provider

    fun search(query: String): List<CampgroundSearchResult>
}
