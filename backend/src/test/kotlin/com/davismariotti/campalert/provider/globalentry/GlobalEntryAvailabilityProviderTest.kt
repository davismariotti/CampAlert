package com.davismariotti.campalert.provider.globalentry

import com.davismariotti.campalert.model.GlobalEntrySearchRequestDetails
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.SearchRequestState
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.provider.CallProtection
import com.davismariotti.campalert.provider.Provider
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import retrofit2.Call
import retrofit2.Response
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GlobalEntryAvailabilityProviderTest {
    private val api = mock(GlobalEntryApi::class.java)
    private val callProtection: CallProtection =
        CallProtection
            .Builder(Provider.GLOBAL_ENTRY)
            .circuitBreaker(CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()))
            .retry(RetryRegistry.of(RetryConfig.custom<Any>().maxAttempts(1).build()))
            .build()
    private val provider = GlobalEntryAvailabilityProvider(api, callProtection).also {
        // 2026-10-20 12:55 in Los Angeles (PDT, UTC-7).
        it.clock = Clock.fixed(Instant.parse("2026-10-20T19:55:00Z"), ZoneOffset.UTC)
    }

    private val user = User(id = 1L, email = "user@example.com", passwordHash = "hash")
    private val centerId = 5446
    private val day = LocalDate.of(2026, 10, 27)

    private fun request(
        id: Long = 1L,
        startDay: LocalDate = day,
        latestStartDay: LocalDate? = null,
        earliest: LocalTime? = null,
        latest: LocalTime? = null,
        timezone: String? = "America/Los_Angeles",
    ): SearchRequest {
        val req = SearchRequest(
            id = id,
            startDay = startDay,
            nights = 1,
            groupSize = 1,
            campsiteId = centerId,
            name = "test",
            userId = 1L,
            provider = Provider.GLOBAL_ENTRY,
            latestStartDay = latestStartDay,
            campgroundTimezone = timezone,
        )
        val state = SearchRequestState()
        state.searchRequest = req
        req.state = state
        if (earliest != null || latest != null) {
            val details = GlobalEntrySearchRequestDetails()
            details.searchRequest = req
            details.earliestTime = earliest
            details.latestTime = latest
            req.globalEntryDetails = details
        }
        return req
    }

    private fun slotsJson(vararg slots: Pair<String, Int>): String = slots.joinToString(prefix = "[", postfix = "]") { (ts, active) -> """{"timestamp":"$ts","active":$active,"total":3}""" }

    private fun stubSlots(json: String) {
        @Suppress("UNCHECKED_CAST")
        val call = mock(Call::class.java) as Call<ResponseBody>
        // A fresh body per execute(): a response body is a one-shot stream.
        `when`(call.execute()).thenAnswer { Response.success(json.toResponseBody("application/json".toMediaType())) }
        `when`(api.getSlots(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(call)
    }

    @Test
    fun `available when a bookable slot falls inside the day and time window`() {
        stubSlots(slotsJson("2026-10-27T10:10" to 1, "2026-10-27T11:40" to 1, "2026-10-27T12:00" to 2, "2026-10-27T12:10" to 0))

        val result = provider.checkAvailability(request(earliest = LocalTime.of(11, 30), latest = LocalTime.of(14, 0)), user)

        assertTrue(result.hasAvailableSites)
        assertEquals(2, result.availableSiteCount)
        assertEquals(day, result.matchedStartDay)
        assertEquals(day, result.matchedEndDay)
        assertEquals(LocalTime.of(11, 40), result.globalEntryMatch!!.earliestStartTime)
        assertEquals(2, result.globalEntryMatch!!.slotCount)
    }

    @Test
    fun `unavailable when the only bookable slot is outside the time window`() {
        stubSlots(slotsJson("2026-10-27T10:10" to 1))

        val result = provider.checkAvailability(request(earliest = LocalTime.of(11, 30), latest = LocalTime.of(14, 0)), user)

        assertFalse(result.hasAvailableSites)
        assertEquals(0, result.availableSiteCount)
        assertNull(result.globalEntryMatch)
        assertNull(result.matchedStartDay)
    }

    @Test
    fun `a request with no window matches any bookable slot on its days`() {
        stubSlots(slotsJson("2026-10-27T07:30" to 1))

        assertTrue(provider.checkAvailability(request(), user).hasAvailableSites)
    }

    @Test
    fun `a same-day slot that already started is ignored`() {
        stubSlots(slotsJson("2026-10-20T08:50" to 1))

        val result = provider.checkAvailability(request(startDay = LocalDate.of(2026, 10, 20)), user)

        assertFalse(result.hasAvailableSites)
    }

    @Test
    fun `a later slot the same day still matches`() {
        stubSlots(slotsJson("2026-10-20T08:50" to 1, "2026-10-20T15:00" to 1))

        val result = provider.checkAvailability(request(startDay = LocalDate.of(2026, 10, 20)), user)

        assertEquals(LocalTime.of(15, 0), result.globalEntryMatch!!.earliestStartTime)
    }

    @Test
    fun `two requests for one center share a single fetch covering the union of their ranges`() {
        stubSlots(slotsJson("2026-10-27T11:40" to 1, "2026-11-05T09:00" to 1))
        val a = request(id = 1L, startDay = LocalDate.of(2026, 10, 26), latestStartDay = LocalDate.of(2026, 10, 28))
        val b = request(id = 2L, startDay = LocalDate.of(2026, 11, 3), latestStartDay = LocalDate.of(2026, 11, 5))

        val cache = provider.newCheckCycleCache(listOf(a, b))
        val resultA = provider.checkAvailability(a, user, cache)
        val resultB = provider.checkAvailability(b, user, cache)

        assertTrue(resultA.hasAvailableSites)
        assertTrue(resultB.hasAvailableSites)
        // First day through the day after the last, exclusive end.
        verify(api, times(1)).getSlots(centerId, "2026-10-26T00:00:00", "2026-11-06T00:00:00")
    }

    @Test
    fun `without a cycle cache each check fetches only its own range`() {
        stubSlots(slotsJson("2026-10-27T11:40" to 1))

        provider.checkAvailability(request(startDay = LocalDate.of(2026, 10, 27), latestStartDay = LocalDate.of(2026, 10, 29)), user)

        verify(api).getSlots(centerId, "2026-10-27T00:00:00", "2026-10-30T00:00:00")
    }

    @Test
    fun `no cycle cache for an empty request list`() {
        assertNull(provider.newCheckCycleCache(emptyList()))
    }

    @Test
    fun `an unsuccessful response is surfaced as an IOException`() {
        @Suppress("UNCHECKED_CAST")
        val call = mock(Call::class.java) as Call<ResponseBody>
        `when`(call.execute()).thenReturn(Response.error(500, "boom".toResponseBody("text/plain".toMediaType())))
        `when`(api.getSlots(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(call)

        assertFailsWith<IOException> { provider.checkAvailability(request(), user) }
    }

    @Test
    fun `a missing or invalid timezone falls back to UTC rather than failing`() {
        stubSlots(slotsJson("2026-10-27T11:40" to 1))

        assertNotNull(provider.checkAvailability(request(timezone = null), user).globalEntryMatch)
        assertNotNull(provider.checkAvailability(request(timezone = "Not/AZone"), user).globalEntryMatch)
    }
}
