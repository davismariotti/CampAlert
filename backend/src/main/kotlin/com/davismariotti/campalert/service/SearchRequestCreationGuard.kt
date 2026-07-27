package com.davismariotti.campalert.service

import com.davismariotti.campalert.exception.ResourceLimitException
import com.davismariotti.campalert.provider.Provider
import org.springframework.stereotype.Service

/**
 * Enforces a user's effective provider access and quotas (combined + per-provider) before a new
 * search request (campground or permit) is created. Shared by both creation delegates so the
 * check has exactly one implementation. See specs/user-resource-limits/spec.md.
 */
@Service
class SearchRequestCreationGuard(
    private val userResourceLimitsService: UserResourceLimitsService,
    private val userActiveRequestCountService: UserActiveRequestCountService,
) {
    fun checkCanCreate(userId: Long, provider: Provider) {
        val allowed = userResourceLimitsService.isProviderAllowed(userId, provider)
        if (!allowed.value) {
            throw ResourceLimitException.ProviderNotAllowed(provider.friendlyName)
        }

        val combinedLimit = userResourceLimitsService.effectiveCombinedMaxActive(userId).value
        val combinedCount = userActiveRequestCountService.combinedActiveCount(userId)
        if (combinedCount >= combinedLimit) {
            throw ResourceLimitException.CombinedQuotaExceeded(combinedLimit)
        }

        val providerLimit = userResourceLimitsService.effectiveProviderMaxActive(userId, provider).value
        if (providerLimit != null) {
            val providerCount = userActiveRequestCountService.providerActiveCount(userId, provider)
            if (providerCount >= providerLimit) {
                throw ResourceLimitException.ProviderQuotaExceeded(provider.friendlyName, providerLimit)
            }
        }
    }
}
