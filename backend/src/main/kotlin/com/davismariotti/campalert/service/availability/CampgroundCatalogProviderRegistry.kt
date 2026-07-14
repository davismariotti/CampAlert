package com.davismariotti.campalert.service.availability

import com.davismariotti.campalert.provider.Provider
import org.springframework.stereotype.Service

@Service
class CampgroundCatalogProviderRegistry(
    private val providers: List<CampgroundCatalogProvider>
) {
    private val byProvider = providers.associateBy { it.provider }

    fun forProvider(provider: Provider): CampgroundCatalogProvider = byProvider[provider] ?: error("No CampgroundCatalogProvider registered for $provider")

    fun all(): List<CampgroundCatalogProvider> = providers
}
