package com.davismariotti.campalert.service.availability

import com.davismariotti.campalert.provider.Provider
import org.springframework.stereotype.Service

@Service
class CampgroundCoordinateProviderRegistry(
    providers: List<CampgroundCoordinateProvider>
) {
    private val byProvider = providers.associateBy { it.provider }

    fun forProvider(provider: Provider): CampgroundCoordinateProvider = byProvider[provider] ?: error("No CampgroundCoordinateProvider registered for $provider")
}
