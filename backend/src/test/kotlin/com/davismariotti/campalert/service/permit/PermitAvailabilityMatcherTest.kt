package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.model.PermitItineraryLeg
import com.davismariotti.campalert.model.PermitItineraryTarget
import com.davismariotti.campalert.model.PermitSearchRequest
import com.davismariotti.campalert.model.PermitSearchRequestState
import com.davismariotti.campalert.model.PermitZoneTarget
import com.davismariotti.campalert.model.SearchType
import com.davismariotti.campalert.recreation.PermitItineraryAvailabilityCell
import com.davismariotti.campalert.recreation.PermitItineraryAvailabilityPayload
import com.davismariotti.campalert.recreation.PermitItineraryAvailabilityResponse
import com.davismariotti.campalert.recreation.PermitZoneAvailabilityCell
import com.davismariotti.campalert.recreation.PermitZoneAvailabilityPayload
import com.davismariotti.campalert.recreation.PermitZoneAvailabilityResponse
import com.davismariotti.campalert.recreation.PermitZoneDivisionAvailability
import com.davismariotti.campalert.recreation.RecreationApi
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import retrofit2.Call
import retrofit2.Response
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

class PermitAvailabilityMatcherTest {
    private val recreationApi = mock(RecreationApi::class.java)
    private val circuitBreakerRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
    private val retryRegistry = RetryRegistry.of(RetryConfig.ofDefaults())

    // Unstubbed Boolean-returning methods default to false in Mockito, so every existing test here
    // sees "not suspicious" unless a test explicitly stubs looksSuspicious to return true.
    private val zoneAvailabilityBaselineService = mock(ZoneAvailabilityBaselineService::class.java)
    private val matcher = PermitAvailabilityMatcher(recreationApi, circuitBreakerRegistry, retryRegistry, zoneAvailabilityBaselineService)

    private val zoneCache: ZoneAvailabilityCache = ConcurrentHashMap()
    private val itineraryCache: ItineraryAvailabilityCache = ConcurrentHashMap()

    @BeforeEach
    fun setUp() {
        circuitBreakerRegistry.circuitBreaker("recreation-gov").reset()
    }

    // eq() returns null as its real-Java sentinel; Kotlin inserts a not-null check on it because
    // getZonePermitAvailability's params are non-null, crashing with "eq(...) must not be null".
    // The unchecked cast sidesteps that (same trick as the anyK() helper elsewhere in this codebase).
    @Suppress("UNCHECKED_CAST")
    private fun <T> eqK(value: T): T = eq(value) as T

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyK(): T = org.mockito.ArgumentMatchers.any<T>() as T

    // Builds the Call *before* the caller opens its own when(...) stub — mockCall() itself calls
    // when(...) internally, so nesting it inside another when(...).thenReturn(...) would corrupt
    // Mockito's stubbing state (UnfinishedStubbingException).
    @Suppress("UNCHECKED_CAST")
    private fun <T> mockCall(body: T): Call<T> {
        val call = mock(Call::class.java) as Call<T>
        `when`(call.execute()).thenReturn(Response.success(body))
        return call
    }

    private fun zoneRequest(divisionIds: List<String>, startDay: LocalDate, endDay: LocalDate): PermitSearchRequest {
        val req = PermitSearchRequest(id = 1L, permitId = "233261", permitName = "Desolation", groupSize = 2, name = "Test", searchType = SearchType.ZONE)
        val state = PermitSearchRequestState()
        state.permitSearchRequest = req
        req.state = state
        val target = PermitZoneTarget()
        target.permitSearchRequest = req
        target.divisionIds = divisionIds
        target.startDay = startDay
        target.endDay = endDay
        req.zoneTarget = target
        return req
    }

    private fun itineraryRequest(legs: List<PermitItineraryLeg>): PermitSearchRequest {
        val req = PermitSearchRequest(id = 2L, permitId = "4675323", permitName = "Yellowstone", groupSize = 2, name = "Test", searchType = SearchType.ITINERARY)
        val state = PermitSearchRequestState()
        state.permitSearchRequest = req
        req.state = state
        val target = PermitItineraryTarget()
        target.permitSearchRequest = req
        target.legs = legs
        req.itineraryTarget = target
        return req
    }

    private fun zoneCell(remaining: Int) = PermitZoneAvailabilityCell(total = 10, remaining = remaining)

    private fun stubZoneAvailability(
        permitId: String,
        availability: Map<String, PermitZoneDivisionAvailability>,
        nextAvailableDate: java.time.ZonedDateTime? = null,
    ) {
        val call = mockCall(
            PermitZoneAvailabilityResponse(
                PermitZoneAvailabilityPayload(permitId = permitId, availability = availability, nextAvailableDate = nextAvailableDate),
            ),
        )
        // getZonePermitAvailability has trailing default params; Kotlin substitutes literal defaults
        // for any not given a matcher, so all four args need matchers here (Mockito's all-or-none rule).
        `when`(recreationApi.getZonePermitAvailability(eqK(permitId), anyString(), anyBoolean(), anyBoolean())).thenReturn(call)
    }

    private fun stubItineraryAvailability(
        permitId: String,
        divisionId: String,
        month: Int,
        year: Int,
        quotaTypeMaps: Map<String, Map<LocalDate, PermitItineraryAvailabilityCell>>
    ) {
        val call = mockCall(PermitItineraryAvailabilityResponse(PermitItineraryAvailabilityPayload(quotaTypeMaps)))
        `when`(recreationApi.getItineraryDivisionAvailability(permitId, divisionId, month, year)).thenReturn(call)
    }

    // --- zone matcher ---

    @Test
    fun `zone matcher marks available when one of several accepted zones has remaining quota`() {
        val request = zoneRequest(divisionIds = listOf("290", "343"), LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 15))
        stubZoneAvailability(
            "233261",
            mapOf(
                "290" to PermitZoneDivisionAvailability("290", mapOf(LocalDate.of(2026, 7, 12).atStartOfDay(ZoneOffset.UTC) to zoneCell(0))),
                "343" to PermitZoneDivisionAvailability("343", mapOf(LocalDate.of(2026, 7, 13).atStartOfDay(ZoneOffset.UTC) to zoneCell(3))),
            ),
        )

        val result = matcher.check(request, zoneCache, itineraryCache)

        assertTrue(result.hasAvailability)
        assertEquals("343", result.matchedDivisionId)
        assertEquals(LocalDate.of(2026, 7, 13), result.matchedDate)
    }

    @Test
    fun `zone matcher marks unavailable when no accepted zone has quota in window`() {
        val request = zoneRequest(divisionIds = listOf("290"), LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 15))
        stubZoneAvailability(
            "233261",
            mapOf("290" to PermitZoneDivisionAvailability("290", mapOf(LocalDate.of(2026, 7, 12).atStartOfDay(ZoneOffset.UTC) to zoneCell(0)))),
        )

        val result = matcher.check(request, zoneCache, itineraryCache)

        assertFalse(result.hasAvailability)
        assertNull(result.matchedDivisionId)
        assertNull(result.matchedDate)
    }

    @Test
    fun `zone matcher ignores dates outside the window`() {
        val request = zoneRequest(divisionIds = listOf("290"), LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 15))
        stubZoneAvailability(
            "233261",
            // outside window, has quota but shouldn't count
            mapOf("290" to PermitZoneDivisionAvailability("290", mapOf(LocalDate.of(2026, 7, 20).atStartOfDay(ZoneOffset.UTC) to zoneCell(5)))),
        )

        val result = matcher.check(request, zoneCache, itineraryCache)

        assertFalse(result.hasAvailability)
    }

    @Test
    fun `zone matcher rejects a division whose data contradicts next_available_date`() {
        val request = zoneRequest(divisionIds = listOf("343"), LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 20))
        stubZoneAvailability(
            "233261",
            // 343 claims July 10 was open, but next_available_date says nothing opened before July 11 —
            // a logical contradiction, so 343's numbers (including the July 15 "match") are distrusted.
            mapOf(
                "343" to PermitZoneDivisionAvailability(
                    "343",
                    mapOf(
                        LocalDate.of(2026, 7, 10).atStartOfDay(ZoneOffset.UTC) to zoneCell(5),
                        LocalDate.of(2026, 7, 15).atStartOfDay(ZoneOffset.UTC) to zoneCell(3),
                    ),
                ),
            ),
            nextAvailableDate = LocalDate.of(2026, 7, 11).atStartOfDay(ZoneOffset.UTC),
        )

        val result = matcher.check(request, zoneCache, itineraryCache)

        assertFalse(result.hasAvailability)
    }

    @Test
    fun `zone matcher still matches a date after next_available_date even if an earlier date is open`() {
        val request = zoneRequest(divisionIds = listOf("343"), LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 20))
        stubZoneAvailability(
            "233261",
            // July 11 is legitimately open (matches next_available_date) and July 12 is what we need —
            // neither date is before the cutoff, so there's no contradiction and the July 12 match stands.
            mapOf(
                "343" to PermitZoneDivisionAvailability(
                    "343",
                    mapOf(
                        LocalDate.of(2026, 7, 11).atStartOfDay(ZoneOffset.UTC) to zoneCell(5),
                        LocalDate.of(2026, 7, 12).atStartOfDay(ZoneOffset.UTC) to zoneCell(3),
                    ),
                ),
            ),
            nextAvailableDate = LocalDate.of(2026, 7, 11).atStartOfDay(ZoneOffset.UTC),
        )

        val result = matcher.check(request, zoneCache, itineraryCache)

        assertTrue(result.hasAvailability)
        assertEquals("343", result.matchedDivisionId)
        assertEquals(LocalDate.of(2026, 7, 11), result.matchedDate)
    }

    @Test
    fun `zone matcher rejects a division that looks suspicious versus its own recorded baseline`() {
        val request = zoneRequest(divisionIds = listOf("343"), LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 15))
        stubZoneAvailability(
            "233261",
            mapOf("343" to PermitZoneDivisionAvailability("343", mapOf(LocalDate.of(2026, 7, 13).atStartOfDay(ZoneOffset.UTC) to zoneCell(3)))),
        )
        `when`(zoneAvailabilityBaselineService.looksSuspicious(eqK("233261"), eqK("343"), anyK(), anyK())).thenReturn(true)

        val result = matcher.check(request, zoneCache, itineraryCache)

        assertFalse(result.hasAvailability)
    }

    // --- itinerary matcher ---

    @Test
    fun `itinerary matcher marks available when every leg has remaining quota`() {
        val legs = listOf(
            PermitItineraryLeg("4675323001", LocalDate.of(2026, 7, 12)),
            PermitItineraryLeg("4675323002", LocalDate.of(2026, 7, 13)),
        )
        val request = itineraryRequest(legs)
        stubItineraryAvailability(
            "4675323",
            "4675323001",
            7,
            2026,
            mapOf("ConstantQuotaUsageDaily" to mapOf(LocalDate.of(2026, 7, 12) to PermitItineraryAvailabilityCell(remaining = 1))),
        )
        stubItineraryAvailability(
            "4675323",
            "4675323002",
            7,
            2026,
            mapOf("ConstantQuotaUsageDaily" to mapOf(LocalDate.of(2026, 7, 13) to PermitItineraryAvailabilityCell(remaining = 1))),
        )

        val result = matcher.check(request, zoneCache, itineraryCache)

        assertTrue(result.hasAvailability)
        assertNull(result.blockingDivisionId)
    }

    @Test
    fun `itinerary matcher marks unavailable when one leg has no quota, regardless of others`() {
        val legs = listOf(
            PermitItineraryLeg("4675323001", LocalDate.of(2026, 7, 12)),
            PermitItineraryLeg("4675323002", LocalDate.of(2026, 7, 13)),
        )
        val request = itineraryRequest(legs)
        stubItineraryAvailability(
            "4675323",
            "4675323001",
            7,
            2026,
            mapOf("ConstantQuotaUsageDaily" to mapOf(LocalDate.of(2026, 7, 12) to PermitItineraryAvailabilityCell(remaining = 0))),
        )

        val result = matcher.check(request, zoneCache, itineraryCache)

        assertFalse(result.hasAvailability)
        assertEquals("4675323001", result.blockingDivisionId)
        assertEquals(LocalDate.of(2026, 7, 12), result.blockingDate)
    }

    @Test
    fun `itinerary matcher requires every quota type map to have remaining quota`() {
        val legs = listOf(PermitItineraryLeg("4675323001", LocalDate.of(2026, 7, 12)))
        val request = itineraryRequest(legs)
        stubItineraryAvailability(
            "4675323",
            "4675323001",
            7,
            2026,
            mapOf(
                "ConstantQuotaUsageDaily" to mapOf(LocalDate.of(2026, 7, 12) to PermitItineraryAvailabilityCell(remaining = 1)),
                "QuotaUsageByMemberDaily" to mapOf(LocalDate.of(2026, 7, 12) to PermitItineraryAvailabilityCell(remaining = 0)),
            ),
        )

        val result = matcher.check(request, zoneCache, itineraryCache)

        assertFalse(result.hasAvailability)
    }

    // --- tick-scoped cache dedup (shared across requests, not just within one check() call) ---

    @Test
    fun `zone availability is fetched only once per tick when two requests share permit and month`() {
        val request1 = zoneRequest(divisionIds = listOf("290"), LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 15))
        val request2 = zoneRequest(divisionIds = listOf("343"), LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 15))
        stubZoneAvailability(
            "233261",
            mapOf(
                "290" to PermitZoneDivisionAvailability("290", mapOf(LocalDate.of(2026, 7, 12).atStartOfDay(ZoneOffset.UTC) to zoneCell(2))),
                "343" to PermitZoneDivisionAvailability("343", mapOf(LocalDate.of(2026, 7, 12).atStartOfDay(ZoneOffset.UTC) to zoneCell(3))),
            ),
        )

        matcher.check(request1, zoneCache, itineraryCache)
        matcher.check(request2, zoneCache, itineraryCache)

        org.mockito.Mockito
            .verify(recreationApi, org.mockito.Mockito.times(1))
            .getZonePermitAvailability(eqK("233261"), anyString(), anyBoolean(), anyBoolean())
    }

    @Test
    fun `itinerary availability is fetched only once per tick when two requests share division and month`() {
        val request1 = itineraryRequest(listOf(PermitItineraryLeg("4675323001", LocalDate.of(2026, 7, 12))))
        val request2 = itineraryRequest(listOf(PermitItineraryLeg("4675323001", LocalDate.of(2026, 7, 20))))
        stubItineraryAvailability(
            "4675323",
            "4675323001",
            7,
            2026,
            mapOf(
                "ConstantQuotaUsageDaily" to
                    mapOf(
                        LocalDate.of(2026, 7, 12) to PermitItineraryAvailabilityCell(remaining = 1),
                        LocalDate.of(2026, 7, 20) to PermitItineraryAvailabilityCell(remaining = 1),
                    ),
            ),
        )

        matcher.check(request1, zoneCache, itineraryCache)
        matcher.check(request2, zoneCache, itineraryCache)

        org.mockito.Mockito
            .verify(recreationApi, org.mockito.Mockito.times(1))
            .getItineraryDivisionAvailability("4675323", "4675323001", 7, 2026)
    }
}
