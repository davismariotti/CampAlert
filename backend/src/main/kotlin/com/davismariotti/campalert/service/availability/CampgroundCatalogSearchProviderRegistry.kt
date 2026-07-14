package com.davismariotti.campalert.service.availability

import com.davismariotti.campalert.provider.Provider
import org.springframework.stereotype.Service

@Service
class CampgroundCatalogSearchProviderRegistry(
    private val providers: List<CampgroundCatalogSearchProvider>
) {
    private val byProvider = providers.associateBy { it.provider }

    fun forProvider(provider: Provider): CampgroundCatalogSearchProvider = byProvider[provider] ?: error("No CampgroundCatalogSearchProvider registered for $provider")

    fun all(): List<CampgroundCatalogSearchProvider> = providers
}
