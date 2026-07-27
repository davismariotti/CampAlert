package com.davismariotti.campalert.service

import com.davismariotti.campalert.exception.ResourceLimitException
import com.davismariotti.campalert.provider.Provider
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class SearchRequestCreationGuardTest {
    private val userResourceLimitsService = mock(UserResourceLimitsService::class.java)
    private val userActiveRequestCountService = mock(UserActiveRequestCountService::class.java)
    private val guard = SearchRequestCreationGuard(userResourceLimitsService, userActiveRequestCountService)

    private val userId = 1L

    private fun allow(provider: Provider) {
        `when`(userResourceLimitsService.isProviderAllowed(userId, provider))
            .thenReturn(UserResourceLimitsService.EffectiveValue(true, UserResourceLimitsService.Source.GLOBAL_DEFAULT))
    }

    private fun deny(provider: Provider) {
        `when`(userResourceLimitsService.isProviderAllowed(userId, provider))
            .thenReturn(UserResourceLimitsService.EffectiveValue(false, UserResourceLimitsService.Source.GLOBAL_DEFAULT))
    }

    private fun combinedLimit(limit: Int) {
        `when`(userResourceLimitsService.effectiveCombinedMaxActive(userId))
            .thenReturn(UserResourceLimitsService.EffectiveValue(limit, UserResourceLimitsService.Source.GLOBAL_DEFAULT))
    }

    private fun providerLimit(provider: Provider, limit: Int?) {
        `when`(userResourceLimitsService.effectiveProviderMaxActive(userId, provider))
            .thenReturn(UserResourceLimitsService.EffectiveValue(limit, UserResourceLimitsService.Source.GLOBAL_DEFAULT))
    }

    @Test
    fun `rejects a disallowed provider`() {
        deny(Provider.RESERVE_CALIFORNIA)

        assertThrows(ResourceLimitException.ProviderNotAllowed::class.java) {
            guard.checkCanCreate(userId, Provider.RESERVE_CALIFORNIA)
        }
    }

    @Test
    fun `rejects when combined quota is already met`() {
        allow(Provider.RECREATION_GOV)
        combinedLimit(5)
        `when`(userActiveRequestCountService.combinedActiveCount(userId)).thenReturn(5L)

        assertThrows(ResourceLimitException.CombinedQuotaExceeded::class.java) {
            guard.checkCanCreate(userId, Provider.RECREATION_GOV)
        }
    }

    @Test
    fun `rejects when per-provider quota is already met even with combined headroom`() {
        allow(Provider.RESERVE_CALIFORNIA)
        combinedLimit(5)
        `when`(userActiveRequestCountService.combinedActiveCount(userId)).thenReturn(1L)
        providerLimit(Provider.RESERVE_CALIFORNIA, 1)
        `when`(userActiveRequestCountService.providerActiveCount(userId, Provider.RESERVE_CALIFORNIA)).thenReturn(1L)

        assertThrows(ResourceLimitException.ProviderQuotaExceeded::class.java) {
            guard.checkCanCreate(userId, Provider.RESERVE_CALIFORNIA)
        }
    }

    @Test
    fun `allows creation when within all limits`() {
        allow(Provider.RECREATION_GOV)
        combinedLimit(5)
        `when`(userActiveRequestCountService.combinedActiveCount(userId)).thenReturn(2L)
        providerLimit(Provider.RECREATION_GOV, null)

        assertDoesNotThrow {
            guard.checkCanCreate(userId, Provider.RECREATION_GOV)
        }
    }
}
