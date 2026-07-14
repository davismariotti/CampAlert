package com.davismariotti.campalert.provider.camplife

import com.davismariotti.campalert.api.model.CampgroundSearchResult
import com.davismariotti.campalert.delegate.toApi
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.service.availability.CampgroundCatalogSearchProvider
import org.springframework.stereotype.Service

/** CampLife's catalog search strategy: an in-memory substring match against the cached global directory (design.md decision 7) — CampLife itself has no server-side search, and its own frontend does the same client-side substring matching after one fetch. */
@Service
class CampLifeCatalogSearchProvider(
    private val campLifeCatalogCache: CampLifeCatalogCache,
) : CampgroundCatalogSearchProvider {
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
}
