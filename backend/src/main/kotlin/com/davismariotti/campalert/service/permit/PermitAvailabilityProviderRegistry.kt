package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.model.Provider
import org.springframework.stereotype.Service

@Service
class PermitAvailabilityProviderRegistry(
    providers: List<PermitAvailabilityProvider>
) {
    private val byProvider = providers.associateBy { it.provider }

    fun forProvider(provider: Provider): PermitAvailabilityProvider = byProvider[provider] ?: error("No PermitAvailabilityProvider registered for $provider")
}
