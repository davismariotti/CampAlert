package com.davismariotti.campalert.service

import com.davismariotti.campalert.model.PauseReason
import com.davismariotti.campalert.model.RecreationGovSearchRequestDetails
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.SearchRequestState
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.repository.PermitSearchRequestRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ResourceReconciliationServiceTest {
    private val searchRequestRepository = mock(SearchRequestRepository::class.java)
    private val permitSearchRequestRepository = mock(PermitSearchRequestRepository::class.java)
    private val userResourceLimitsService = mock(UserResourceLimitsService::class.java)
    private val service = ResourceReconciliationService(searchRequestRepository, permitSearchRequestRepository, userResourceLimitsService)

    private val userId = 1L

    private fun request(
        id: Long,
        provider: Provider,
        ageMinutesAgo: Long,
        pauseReason: String? = null
    ): SearchRequest {
        val req = SearchRequest(
            id = id,
            startDay = LocalDate.now().plusDays(30),
            nights = 2,
            groupSize = 2,
            campsiteId = 100,
            name = "req-$id",
            userId = userId,
            provider = provider,
            createdAt = Instant.now().minus(ageMinutesAgo, ChronoUnit.MINUTES),
        )
        val state = SearchRequestState()
        state.searchRequest = req
        state.pauseReason = pauseReason
        req.state = state
        val details = RecreationGovSearchRequestDetails()
        details.searchRequest = req
        req.recreationGovDetails = details
        return req
    }

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
    fun `pauses the newest requests first when over the combined quota`() {
        val oldest = request(1, Provider.RECREATION_GOV, ageMinutesAgo = 30)
        val middle = request(2, Provider.RECREATION_GOV, ageMinutesAgo = 20)
        val newest = request(3, Provider.RECREATION_GOV, ageMinutesAgo = 10)
        `when`(searchRequestRepository.findActiveByUserId(userId)).thenReturn(listOf(oldest, middle, newest))
        `when`(permitSearchRequestRepository.findActiveByUserId(userId)).thenReturn(emptyList())
        allow(Provider.RECREATION_GOV)
        providerLimit(Provider.RECREATION_GOV, null)
        combinedLimit(2)

        service.reconcileUser(userId)

        assertNull(oldest.state.pauseReason)
        assertNull(middle.state.pauseReason)
        assertEquals(PauseReason.QUOTA_EXCEEDED.name, newest.state.pauseReason)
    }

    @Test
    fun `resumes a previously quota-paused request once headroom opens up`() {
        val oldest = request(1, Provider.RECREATION_GOV, ageMinutesAgo = 30, pauseReason = PauseReason.QUOTA_EXCEEDED.name)
        `when`(searchRequestRepository.findActiveByUserId(userId)).thenReturn(listOf(oldest))
        `when`(permitSearchRequestRepository.findActiveByUserId(userId)).thenReturn(emptyList())
        allow(Provider.RECREATION_GOV)
        providerLimit(Provider.RECREATION_GOV, null)
        combinedLimit(5)

        service.reconcileUser(userId)

        assertNull(oldest.state.pauseReason)
    }

    @Test
    fun `pauses all active requests for a provider that is no longer allowed`() {
        val rc = request(1, Provider.RESERVE_CALIFORNIA, ageMinutesAgo = 5)
        `when`(searchRequestRepository.findActiveByUserId(userId)).thenReturn(listOf(rc))
        `when`(permitSearchRequestRepository.findActiveByUserId(userId)).thenReturn(emptyList())
        deny(Provider.RESERVE_CALIFORNIA)
        combinedLimit(5)

        service.reconcileUser(userId)

        assertEquals(PauseReason.PROVIDER_DISABLED.name, rc.state.pauseReason)
    }

    @Test
    fun `never touches a request paused for missing verified phone`() {
        val phonePaused = request(1, Provider.RECREATION_GOV, ageMinutesAgo = 5, pauseReason = PauseReason.NO_VERIFIED_PHONE.name)
        `when`(searchRequestRepository.findActiveByUserId(userId)).thenReturn(listOf(phonePaused))
        `when`(permitSearchRequestRepository.findActiveByUserId(userId)).thenReturn(emptyList())

        service.reconcileUser(userId)

        assertEquals(PauseReason.NO_VERIFIED_PHONE.name, phonePaused.state.pauseReason)
    }

    @Test
    fun `per-provider quota is enforced independently of combined headroom`() {
        val rc1 = request(1, Provider.RESERVE_CALIFORNIA, ageMinutesAgo = 20)
        val rc2 = request(2, Provider.RESERVE_CALIFORNIA, ageMinutesAgo = 10)
        `when`(searchRequestRepository.findActiveByUserId(userId)).thenReturn(listOf(rc1, rc2))
        `when`(permitSearchRequestRepository.findActiveByUserId(userId)).thenReturn(emptyList())
        allow(Provider.RESERVE_CALIFORNIA)
        providerLimit(Provider.RESERVE_CALIFORNIA, 1)
        combinedLimit(5)

        service.reconcileUser(userId)

        assertNull(rc1.state.pauseReason)
        assertEquals(PauseReason.QUOTA_EXCEEDED.name, rc2.state.pauseReason)
    }
}
