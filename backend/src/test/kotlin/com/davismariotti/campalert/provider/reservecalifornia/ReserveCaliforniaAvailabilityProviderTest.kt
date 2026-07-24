package com.davismariotti.campalert.provider.reservecalifornia

import com.davismariotti.campalert.model.ReserveCaliforniaSearchRequestDetails
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.SearchRequestState
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.provider.CallProtection
import com.davismariotti.campalert.provider.Provider
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import retrofit2.Call
import retrofit2.Response
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReserveCaliforniaAvailabilityProviderTest {
    private val reserveCaliforniaApi = mock(ReserveCaliforniaApi::class.java)
    private val occupancyService = mock(ReserveCaliforniaOccupancyService::class.java)
    private val callProtection: CallProtection =
        CallProtection
            .Builder(Provider.RESERVE_CALIFORNIA)
            .circuitBreaker(CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()))
            .retry(RetryRegistry.of(RetryConfig.ofDefaults()))
            .build()

    private val provider = ReserveCaliforniaAvailabilityProvider(reserveCaliforniaApi, callProtection, occupancyService)

    private val user = User(id = 1L, email = "user@example.com", passwordHash = "hash")
    private val facilityId = 585
    private val startDay: LocalDate = LocalDate.now().plusDays(10)

    private fun request(
        siteIds: List<String>? = null,
        groupSize: Int = 1,
        nights: Int = 2,
    ): SearchRequest {
        val req = SearchRequest(
            id = 1L,
            startDay = startDay,
            nights = nights,
            groupSize = groupSize,
            campsiteId = facilityId,
            siteIds = siteIds,
            name = "test",
            userId = 1L,
            provider = Provider.RESERVE_CALIFORNIA,
        )
        val state = SearchRequestState()
        state.searchRequest = req
        req.state = state
        val details = ReserveCaliforniaSearchRequestDetails()
        details.searchRequest = req
        details.placeId = 683
        req.reserveCaliforniaDetails = details
        return req
    }

    private fun unit(unitId: Int, isFiltered: Boolean = false, freeAllNights: Boolean = true): ReserveCaliforniaGridUnit {
        val slices = if (freeAllNights) {
            (0 until 2).associate { "${startDay.plusDays(it.toLong())}T00:00:00" to ReserveCaliforniaSlice(isFree = true) }
        } else {
            mapOf("${startDay}T00:00:00" to ReserveCaliforniaSlice(isFree = false))
        }
        return ReserveCaliforniaGridUnit(unitId = unitId, name = "Site $unitId", isFiltered = isFiltered, slices = slices)
    }

    private fun mockGrid(units: List<ReserveCaliforniaGridUnit>) {
        @Suppress("UNCHECKED_CAST")
        val call = mock(Call::class.java) as Call<ReserveCaliforniaGridResponse>
        `when`(call.execute()).thenReturn(
            Response.success(
                ReserveCaliforniaGridResponse(
                    facility = ReserveCaliforniaGridFacility(facilityId = facilityId, units = units.associateBy { "bucket.${it.unitId}" }),
                ),
            ),
        )
        `when`(reserveCaliforniaApi.getGrid(any())).thenReturn(call)
    }

    @Test
    fun `a unit flagged isFiltered never matches even when free`() {
        mockGrid(listOf(unit(1, isFiltered = true)))

        val result = provider.checkAvailability(request(), user)

        assertFalse(result.hasAvailableSites)
    }

    @Test
    fun `a unit not free for every night never matches`() {
        mockGrid(listOf(unit(1, freeAllNights = false)))

        val result = provider.checkAvailability(request(), user)

        assertFalse(result.hasAvailableSites)
    }

    @Test
    fun `groupSize of 1 or less matches without consulting occupancy at all`() {
        mockGrid(listOf(unit(1)))

        val result = provider.checkAvailability(request(groupSize = 1), user)

        assertTrue(result.hasAvailableSites)
        assertEquals(setOf("1"), result.availableSiteIds)
        verify(occupancyService, never()).findFetchedSufficientFor(any(), any(), any())
        verify(occupancyService, never()).resolveSufficientForSiteIds(any(), any(), any())
    }

    @Test
    fun `any-site groupSize search only matches units occupancy confirms as sufficient`() {
        mockGrid(listOf(unit(1), unit(2)))
        `when`(occupancyService.findFetchedSufficientFor(eq(facilityId), any(), eq(6))).thenReturn(setOf(2))

        val result = provider.checkAvailability(request(groupSize = 6), user)

        assertTrue(result.hasAvailableSites)
        assertEquals(setOf("2"), result.availableSiteIds)
    }

    @Test
    fun `any-site groupSize search matches nothing when occupancy is entirely unfetched`() {
        mockGrid(listOf(unit(1), unit(2)))
        `when`(occupancyService.findFetchedSufficientFor(eq(facilityId), any(), eq(6))).thenReturn(emptySet())

        val result = provider.checkAvailability(request(groupSize = 6), user)

        assertFalse(result.hasAvailableSites)
    }

    @Test
    fun `site_ids scoping restricts candidates before occupancy resolution and never touches unrequested units`() {
        mockGrid(listOf(unit(1), unit(2), unit(3)))
        `when`(occupancyService.resolveSufficientForSiteIds(eq(facilityId), eq(setOf(2)), eq(4))).thenReturn(setOf(2))

        val result = provider.checkAvailability(request(siteIds = listOf("2"), groupSize = 4), user)

        assertTrue(result.hasAvailableSites)
        assertEquals(setOf("2"), result.availableSiteIds)
        verify(occupancyService).resolveSufficientForSiteIds(eq(facilityId), eq(setOf(2)), eq(4))
    }

    @Test
    fun `exception during the grid call resolves to zero availability`() {
        @Suppress("UNCHECKED_CAST")
        val call = mock(Call::class.java) as Call<ReserveCaliforniaGridResponse>
        `when`(call.execute()).thenThrow(RuntimeException("network error"))
        `when`(reserveCaliforniaApi.getGrid(any())).thenReturn(call)

        val result = provider.checkAvailability(request(), user)

        assertFalse(result.hasAvailableSites)
    }
}
