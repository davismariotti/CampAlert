package com.davismariotti.campalert.provider.recreation

import com.davismariotti.campalert.model.RecreationGovSearchRequestDetails
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
import org.mockito.Mockito.`when`
import retrofit2.Call
import retrofit2.Response
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecreationServiceImplTest {
    private val recreationApi = mock(RecreationApi::class.java)
    private val callProtection: CallProtection =
        CallProtection
            .Builder(Provider.RECREATION_GOV)
            .circuitBreaker(CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()))
            .retry(RetryRegistry.of(RetryConfig.ofDefaults()))
            .build()

    private val service = RecreationServiceImpl(recreationApi, callProtection, requestJitterMs = 0)

    private val user = User(id = 1L, email = "user@example.com", passwordHash = "hash")
    private val campsiteId = 233359

    private fun request(
        startDay: LocalDate,
        nights: Int,
        latestStartDay: LocalDate? = null,
        loops: List<String>? = null,
        siteIds: List<String>? = null,
        groupSize: Int = 2,
    ): SearchRequest {
        val req = SearchRequest(
            id = 1L,
            startDay = startDay,
            nights = nights,
            groupSize = groupSize,
            campsiteId = campsiteId,
            siteIds = siteIds,
            name = "test",
            userId = 1L,
            provider = Provider.RECREATION_GOV,
            latestStartDay = latestStartDay,
        )
        val state = SearchRequestState()
        state.searchRequest = req
        req.state = state
        if (!loops.isNullOrEmpty()) {
            val details = RecreationGovSearchRequestDetails()
            details.searchRequest = req
            details.loops = loops
            req.recreationGovDetails = details
        }
        return req
    }

    /** One campsite whose `AVAILABLE` nights are exactly [availableDates]; every other date in [allDates] is `RESERVED`. */
    private fun campgroundWith(
        allDates: List<LocalDate>,
        availableDates: Set<LocalDate>,
        siteId: Int = 5001,
        loop: String = "A Loop"
    ): Campground {
        val availabilities = allDates.associate { date ->
            ZonedDateTime.of(date.atStartOfDay(), ZoneOffset.UTC) to (if (date in availableDates) AvailabilityType.AVAILABLE else AvailabilityType.RESERVED)
        }
        val site = Campsite(
            campsiteId = siteId,
            site = "A1",
            loop = loop,
            campsiteReserveType = "Site-Specific",
            availabilities = availabilities,
            quantities = emptyMap(),
            minimumNumberOfPeople = 1,
            maximumNumberOfPeople = 8,
        )
        return Campground(campsites = mapOf(siteId to site))
    }

    /** Every month call returns the same full-range campground — sufficient since matching happens locally after merge. */
    private fun mockMonthlyFetch(campground: Campground) {
        @Suppress("UNCHECKED_CAST")
        val call = mock(Call::class.java) as Call<Campground>
        `when`(call.execute()).thenReturn(Response.success(campground))
        `when`(recreationApi.getCampgroundAvailability(org.mockito.kotlin.eq(campsiteId), org.mockito.kotlin.any())).thenReturn(call)
    }

    @Test
    fun `exact-date match requires every night available and sets matchedStartDay to startDay`() {
        val startDay = LocalDate.now().plusDays(10)
        val allDates = listOf(startDay, startDay.plusDays(1))
        mockMonthlyFetch(campgroundWith(allDates, availableDates = allDates.toSet()))

        val result = service.checkAvailability(request(startDay, nights = 2), user, null)

        assertTrue(result.hasAvailableSites)
        assertEquals(startDay, result.matchedStartDay)
        assertEquals(startDay.plusDays(2), result.matchedEndDay)
    }

    @Test
    fun `exact-date request with one unavailable night in the stay is unavailable`() {
        val startDay = LocalDate.now().plusDays(10)
        val allDates = listOf(startDay, startDay.plusDays(1))
        mockMonthlyFetch(campgroundWith(allDates, availableDates = setOf(startDay)))

        val result = service.checkAvailability(request(startDay, nights = 2), user, null)

        assertFalse(result.hasAvailableSites)
        assertNull(result.matchedStartDay)
    }

    @Test
    fun `flexible search selects the earliest matching candidate window`() {
        val startDay = LocalDate.now().plusDays(10)
        // latestStartDay = startDay+2, nights=2 -> candidates are startDay, +1, +2 directly (no
        // subtraction for nights). Only the +1/+2 pair is fully available, so the second candidate
        // (startDay+1) should win.
        val allDates = (0..4).map { startDay.plusDays(it.toLong()) }
        mockMonthlyFetch(campgroundWith(allDates, availableDates = setOf(startDay.plusDays(1), startDay.plusDays(2))))

        val result = service.checkAvailability(request(startDay, nights = 2, latestStartDay = startDay.plusDays(2)), user, null)

        assertTrue(result.hasAvailableSites)
        assertEquals(startDay.plusDays(1), result.matchedStartDay)
        assertEquals(startDay.plusDays(3), result.matchedEndDay)
    }

    @Test
    fun `candidate arrival dates run through latestStartDay directly, independent of nights`() {
        val startDay = LocalDate.now().plusDays(10)
        // nights=5 here would previously have shrunk the candidate range (lastArrival = latestStartDay
        // - nights); now the candidate range is exactly [startDay, latestStartDay] regardless of nights.
        val latestStartDay = startDay.plusDays(3)
        val allDates = (0..8).map { startDay.plusDays(it.toLong()) }
        // Only the very last candidate (startDay+3) is fully available for a 5-night stay.
        mockMonthlyFetch(campgroundWith(allDates, availableDates = (3..8).map { startDay.plusDays(it.toLong()) }.toSet()))

        val result = service.checkAvailability(request(startDay, nights = 5, latestStartDay = latestStartDay), user, null)

        assertTrue(result.hasAvailableSites)
        assertEquals(startDay.plusDays(3), result.matchedStartDay)
        assertEquals(startDay.plusDays(8), result.matchedEndDay)
    }

    @Test
    fun `flexible search spanning multiple months still finds a match`() {
        val startDay = LocalDate
            .now()
            .withDayOfMonth(1)
            .plusMonths(1)
            .minusDays(1) // last day of a month
        val latestStartDay = startDay.plusDays(3)
        val allDates = (0..5).map { startDay.plusDays(it.toLong()) }
        // Only the last candidate's two nights (crossing into the next month) are available.
        mockMonthlyFetch(campgroundWith(allDates, availableDates = setOf(startDay.plusDays(3), startDay.plusDays(4))))

        val result = service.checkAvailability(request(startDay, nights = 2, latestStartDay = latestStartDay), user, null)

        assertTrue(result.hasAvailableSites)
        assertEquals(startDay.plusDays(3), result.matchedStartDay)
        assertEquals(startDay.plusDays(5), result.matchedEndDay)
    }

    @Test
    fun `flexible search with no matching candidate anywhere in the range is unavailable`() {
        val startDay = LocalDate.now().plusDays(10)
        val allDates = (0..4).map { startDay.plusDays(it.toLong()) }
        mockMonthlyFetch(campgroundWith(allDates, availableDates = emptySet()))

        val result = service.checkAvailability(request(startDay, nights = 2, latestStartDay = startDay.plusDays(2)), user, null)

        assertFalse(result.hasAvailableSites)
        assertNull(result.matchedStartDay)
        assertNull(result.matchedEndDay)
    }

    @Test
    fun `loop filter still applies per candidate in flexible mode`() {
        val startDay = LocalDate.now().plusDays(10)
        val allDates = (0..2).map { startDay.plusDays(it.toLong()) }
        mockMonthlyFetch(campgroundWith(allDates, availableDates = allDates.toSet(), loop = "B Loop"))

        val result = service.checkAvailability(request(startDay, nights = 1, latestStartDay = startDay.plusDays(1), loops = listOf("A Loop")), user, null)

        assertFalse(result.hasAvailableSites)
    }
}
