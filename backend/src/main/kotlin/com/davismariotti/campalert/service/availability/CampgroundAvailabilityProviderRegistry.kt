package com.davismariotti.campalert.service.availability

import com.davismariotti.campalert.provider.Provider
import org.springframework.stereotype.Service

@Service
class CampgroundAvailabilityProviderRegistry(
    providers: List<CampgroundAvailabilityProvider>
) {
    private val byProvider = providers.associateBy { it.provider }

    fun forProvider(provider: Provider): CampgroundAvailabilityProvider = byProvider[provider] ?: error("No CampgroundAvailabilityProvider registered for $provider")
}
