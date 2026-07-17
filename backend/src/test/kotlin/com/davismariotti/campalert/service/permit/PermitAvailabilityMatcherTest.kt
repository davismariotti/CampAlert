package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.model.AvailabilityState
import com.davismariotti.campalert.model.PermitItineraryLeg
import com.davismariotti.campalert.model.PermitItineraryTarget
import com.davismariotti.campalert.model.PermitSearchRequest
import com.davismariotti.campalert.model.PermitSearchRequestState
import com.davismariotti.campalert.model.PermitTrailheadTarget
import com.davismariotti.campalert.model.PermitZoneTarget
import com.davismariotti.campalert.model.SearchType
import com.davismariotti.campalert.provider.CallProtection
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.provider.recreation.PermitContentResponse
import com.davismariotti.campalert.provider.recreation.PermitDivisionAvailabilityPayload
import com.davismariotti.campalert.provider.recreation.PermitDivisionAvailabilityResponse
import com.davismariotti.campalert.provider.recreation.PermitDivisionType
import com.davismariotti.campalert.provider.recreation.PermitItineraryAvailabilityCell
import com.davismariotti.campalert.provider.recreation.PermitItineraryAvailabilityPayload
import com.davismariotti.campalert.provider.recreation.PermitItineraryAvailabilityResponse
import com.davismariotti.campalert.provider.recreation.PermitQuotaType
import com.davismariotti.campalert.provider.recreation.PermitRuleName
import com.davismariotti.campalert.provider.recreation.PermitTrailheadAvailabilityCell
import com.davismariotti.campalert.provider.recreation.PermitTrailheadAvailabilityResponse
import com.davismariotti.campalert.provider.recreation.PermitTrailheadQuotaGate
import com.davismariotti.campalert.provider.recreation.PermitZoneAvailabilityCell
import com.davismariotti.campalert.provider.recreation.PermitZoneAvailabilityPayload
import com.davismariotti.campalert.provider.recreation.PermitZoneAvailabilityResponse
import com.davismariotti.campalert.provider.recreation.PermitZoneDivisionAvailability
import com.davismariotti.campalert.provider.recreation.RecreationApi
import com.davismariotti.campalert.provider.recreation.SearchEntityType
import com.davismariotti.campalert.provider.recreation.SearchSuggestResponse
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
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
    private val rateLimiterRegistry = RateLimiterRegistry.of(
        RateLimiterConfig
            .custom()
            .limitForPeriod(1000)
            .limitRefreshPeriod(java.time.Duration.ofMillis(1))
            .timeoutDuration(java.time.Duration.ofSeconds(5))
            .build(),
    )
    private val callProtection: CallProtection = CallProtection
        .Builder(Provider.RECREATION_GOV)
        .circuitBreaker(circuitBreakerRegistry)
        .retry(retryRegistry)
        .rateLimiter(rateLimiterRegistry)
        .build()

    // Unstubbed Boolean-returning methods default to false in Mockito, so every existing test here
    // sees "not suspicious" unless a test explicitly stubs looksSuspicious to return true.
    private val zoneAvailabilityBaselineService = mock(ZoneAvailabilityBaselineService::class.java)
    private val matcher = PermitAvailabilityMatcher(recreationApi, callProtection, zoneAvailabilityBaselineService)

    private val zoneCache: ZoneAvailabilityCache = ConcurrentHashMap()
    private val itineraryCache: ItineraryAvailabilityCache = ConcurrentHashMap()
    private val trailheadCache: TrailheadAvailabilityCache = ConcurrentHashMap()

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

    private fun zoneRequest(
        divisionIds: List<String>,
        startDay: LocalDate,
        endDay: LocalDate,
        groupSize: Int = 2
    ): PermitSearchRequest {
        val req = PermitSearchRequest(id = 1L, permitId = "233261", permitName = "Desolation", groupSize = groupSize, name = "Test", searchType = SearchType.ZONE)
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

    private fun itineraryRequest(legs: List<PermitItineraryLeg>, groupSize: Int = 2): PermitSearchRequest {
        val req = PermitSearchRequest(id = 2L, permitId = "4675323", permitName = "Yellowstone", groupSize = groupSize, name = "Test", searchType = SearchType.ITINERARY)
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

    // Stubs the independent corroboration endpoint hit for a fresh (non-AVAILABLE) transition's
    // candidate match. Tests asserting a fresh match must stub this or the unstubbed Call mock returns
    // null, corroboration fails closed, and the real match gets rejected.
    private fun stubDivisionAvailability(
        permitId: String,
        divisionId: String,
        date: LocalDate,
        remaining: Int,
        total: Int = 25
    ) {
        val call = mockCall(
            PermitDivisionAvailabilityResponse(
                PermitDivisionAvailabilityPayload(
                    permitId = permitId,
                    dateAvailability = mapOf(date.atStartOfDay(ZoneOffset.UTC) to PermitZoneAvailabilityCell(total = total, remaining = remaining)),
                ),
            ),
        )
        `when`(
            recreationApi.getDivisionAvailability(eqK(permitId), eqK(divisionId), anyString(), anyString(), anyBoolean(), anyBoolean()),
        ).thenReturn(call)
    }

    private fun stubItineraryAvailability(
        permitId: String,
        divisionId: String,
        month: Int,
        year: Int,
        quotaTypeMaps: Map<PermitQuotaType, Map<LocalDate, PermitItineraryAvailabilityCell>>
    ) {
        val call = mockCall(PermitItineraryAvailabilityResponse(PermitItineraryAvailabilityPayload(quotaTypeMaps)))
        `when`(recreationApi.getItineraryDivisionAvailability(permitId, divisionId, month, year)).thenReturn(call)
    }

    private fun trailheadRequest(
        divisionIds: List<String>,
        startDay: LocalDate,
        endDay: LocalDate,
        groupSize: Int = 2
    ): PermitSearchRequest {
        val req = PermitSearchRequest(id = 3L, permitId = "445859", permitName = "Yosemite", groupSize = groupSize, name = "Test", searchType = SearchType.TRAILHEAD)
        val state = PermitSearchRequestState()
        state.permitSearchRequest = req
        req.state = state
        val target = PermitTrailheadTarget()
        target.permitSearchRequest = req
        target.divisionIds = divisionIds
        target.startDay = startDay
        target.endDay = endDay
        req.trailheadTarget = target
        return req
    }

    private fun trailheadCell(gates: Map<String, PermitTrailheadQuotaGate>, notYetReleased: Boolean = false) = PermitTrailheadAvailabilityCell(isWalkup = false, notYetReleased = notYetReleased, quotaGates = gates)

    private fun singleGateCell(remaining: Int, total: Int = 10) = trailheadCell(mapOf("quota_usage_by_member_daily" to PermitTrailheadQuotaGate(total = total, remaining = remaining)))

    private fun stubTrailheadAvailability(permitId: String, payload: Map<LocalDate, Map<String, PermitTrailheadAvailabilityCell>>) {
        val call = mockCall(PermitTrailheadAvailabilityResponse(payload))
        `when`(recreationApi.getTrailheadPermitAvailability(eqK(permitId), anyString(), anyString(), anyBoolean())).thenReturn(call)
    }

    // Stubs the trailhead corroboration endpoint hit for a fresh (non-AVAILABLE) transition's candidate
    // match — same reasoning as stubDivisionAvailability.
    private fun stubTrailheadDivisionAvailability(
        permitId: String,
        divisionId: String,
        date: LocalDate,
        cell: PermitTrailheadAvailabilityCell?
    ) {
        val payload = if (cell != null) mapOf(date to mapOf(divisionId to cell)) else emptyMap()
        val call = mockCall(PermitTrailheadAvailabilityResponse(payload))
        `when`(
            recreationApi.getTrailheadDivisionAvailability(eqK(permitId), eqK(divisionId), anyString(), anyString(), anyBoolean()),
        ).thenReturn(call)
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
        stubDivisionAvailability("233261", "343", LocalDate.of(2026, 7, 13), remaining = 3)

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

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

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

        assertFalse(result.hasAvailability)
        assertNull(result.matchedDivisionId)
        assertNull(result.matchedDate)
    }

    @Test
    fun `zone matcher marks unavailable when remaining quota is nonzero but too small for the group`() {
        // Mirrors a real Desolation Wilderness capture: total=25, remaining=1 on a date, but the
        // request's group is 4 — one PAX slot left can't seat the whole group.
        val request = zoneRequest(divisionIds = listOf("343"), LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 20), groupSize = 4)
        stubZoneAvailability(
            "233261",
            mapOf("343" to PermitZoneDivisionAvailability("343", mapOf(LocalDate.of(2026, 7, 17).atStartOfDay(ZoneOffset.UTC) to zoneCell(1)))),
        )

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

        assertFalse(result.hasAvailability)
    }

    @Test
    fun `zone matcher marks available when remaining quota exactly fits the group`() {
        val request = zoneRequest(divisionIds = listOf("343"), LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 20), groupSize = 4)
        stubZoneAvailability(
            "233261",
            mapOf("343" to PermitZoneDivisionAvailability("343", mapOf(LocalDate.of(2026, 7, 17).atStartOfDay(ZoneOffset.UTC) to zoneCell(4)))),
        )
        stubDivisionAvailability("233261", "343", LocalDate.of(2026, 7, 17), remaining = 4)

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

        assertTrue(result.hasAvailability)
    }

    @Test
    fun `zone matcher ignores dates outside the window`() {
        val request = zoneRequest(divisionIds = listOf("290"), LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 15))
        stubZoneAvailability(
            "233261",
            // outside window, has quota but shouldn't count
            mapOf("290" to PermitZoneDivisionAvailability("290", mapOf(LocalDate.of(2026, 7, 20).atStartOfDay(ZoneOffset.UTC) to zoneCell(5)))),
        )

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

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

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

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
        stubDivisionAvailability("233261", "343", LocalDate.of(2026, 7, 11), remaining = 5)

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

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

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

        assertFalse(result.hasAvailability)
    }

    @Test
    fun `zone matcher rejects an otherwise-clean division when two sibling target divisions look suspicious in the same tick`() {
        // 343 itself passes both individual checks, but 100 and 200 (also targeted by this request)
        // both flip suspicious in this same response — a rotating, payload-wide glitch pattern
        // confirmed live, not a single bad division — so 343's own clean-looking match is distrusted too.
        val request = zoneRequest(divisionIds = listOf("100", "200", "343"), LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 15))
        stubZoneAvailability(
            "233261",
            mapOf(
                "100" to PermitZoneDivisionAvailability("100", mapOf(LocalDate.of(2026, 7, 12).atStartOfDay(ZoneOffset.UTC) to zoneCell(0))),
                "200" to PermitZoneDivisionAvailability("200", mapOf(LocalDate.of(2026, 7, 12).atStartOfDay(ZoneOffset.UTC) to zoneCell(0))),
                "343" to PermitZoneDivisionAvailability("343", mapOf(LocalDate.of(2026, 7, 13).atStartOfDay(ZoneOffset.UTC) to zoneCell(3))),
            ),
        )
        `when`(zoneAvailabilityBaselineService.looksSuspicious(eqK("233261"), eqK("100"), anyK(), anyK())).thenReturn(true)
        `when`(zoneAvailabilityBaselineService.looksSuspicious(eqK("233261"), eqK("200"), anyK(), anyK())).thenReturn(true)

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

        assertFalse(result.hasAvailability)
    }

    @Test
    fun `zone matcher rejects a candidate match that fails independent per-division corroboration`() {
        val request = zoneRequest(divisionIds = listOf("343"), LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 15))
        stubZoneAvailability(
            "233261",
            mapOf("343" to PermitZoneDivisionAvailability("343", mapOf(LocalDate.of(2026, 7, 13).atStartOfDay(ZoneOffset.UTC) to zoneCell(3)))),
        )
        // The zone endpoint says 343 has room on 7/13, but the independent per-division endpoint
        // disagrees (remaining=0) — this is exactly the disagreement pattern a real ghost-availability
        // response would produce, since the two endpoints are confirmed live to be computed independently.
        stubDivisionAvailability("233261", "343", LocalDate.of(2026, 7, 13), remaining = 0)

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

        assertFalse(result.hasAvailability)
    }

    @Test
    fun `zone matcher skips corroboration when the request is already AVAILABLE (steady state, no fresh notification)`() {
        val request = zoneRequest(divisionIds = listOf("343"), LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 15))
        request.state.lastAvailabilityState = AvailabilityState.AVAILABLE
        stubZoneAvailability(
            "233261",
            mapOf("343" to PermitZoneDivisionAvailability("343", mapOf(LocalDate.of(2026, 7, 13).atStartOfDay(ZoneOffset.UTC) to zoneCell(3)))),
        )
        // Deliberately not stubbing getDivisionAvailability — if the matcher called it, the unstubbed
        // Call mock would return null and corroboration would fail closed, so this only passes if the
        // match short-circuits corroboration for an already-AVAILABLE request.

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

        assertTrue(result.hasAvailability)
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
            mapOf(PermitQuotaType.ConstantQuotaUsageDaily to mapOf(LocalDate.of(2026, 7, 12) to PermitItineraryAvailabilityCell(remaining = 1))),
        )
        stubItineraryAvailability(
            "4675323",
            "4675323002",
            7,
            2026,
            mapOf(PermitQuotaType.ConstantQuotaUsageDaily to mapOf(LocalDate.of(2026, 7, 13) to PermitItineraryAvailabilityCell(remaining = 1))),
        )

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

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
            mapOf(PermitQuotaType.ConstantQuotaUsageDaily to mapOf(LocalDate.of(2026, 7, 12) to PermitItineraryAvailabilityCell(remaining = 0))),
        )

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

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
                PermitQuotaType.ConstantQuotaUsageDaily to mapOf(LocalDate.of(2026, 7, 12) to PermitItineraryAvailabilityCell(remaining = 1)),
                PermitQuotaType.QuotaUsageByMemberDaily to mapOf(LocalDate.of(2026, 7, 12) to PermitItineraryAvailabilityCell(remaining = 0)),
            ),
        )

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

        assertFalse(result.hasAvailability)
    }

    @Test
    fun `itinerary matcher requires the MEMBER quota to fit the whole group, but CONSTANT only needs one slot`() {
        val legs = listOf(PermitItineraryLeg("4675323001", LocalDate.of(2026, 7, 12)))
        val request = itineraryRequest(legs, groupSize = 4)
        stubItineraryAvailability(
            "4675323",
            "4675323001",
            7,
            2026,
            mapOf(
                // One permit slot is plenty (CONSTANT is flat, not PAX-based)...
                PermitQuotaType.ConstantQuotaUsageDaily to mapOf(LocalDate.of(2026, 7, 12) to PermitItineraryAvailabilityCell(remaining = 1)),
                // ...but only 3 PAX remain in the member quota, not enough to seat a group of 4.
                PermitQuotaType.QuotaUsageByMemberDaily to mapOf(LocalDate.of(2026, 7, 12) to PermitItineraryAvailabilityCell(remaining = 3)),
            ),
        )

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

        assertFalse(result.hasAvailability)
    }

    @Test
    fun `itinerary matcher matches when the MEMBER quota has room for the whole group`() {
        val legs = listOf(PermitItineraryLeg("4675323001", LocalDate.of(2026, 7, 12)))
        val request = itineraryRequest(legs, groupSize = 4)
        stubItineraryAvailability(
            "4675323",
            "4675323001",
            7,
            2026,
            mapOf(
                PermitQuotaType.ConstantQuotaUsageDaily to mapOf(LocalDate.of(2026, 7, 12) to PermitItineraryAvailabilityCell(remaining = 1)),
                PermitQuotaType.QuotaUsageByMemberDaily to mapOf(LocalDate.of(2026, 7, 12) to PermitItineraryAvailabilityCell(remaining = 4)),
            ),
        )

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

        assertTrue(result.hasAvailability)
    }

    @Test
    fun `itinerary matcher treats an unrecognized quota type as PAX-based, not as a flat slot`() {
        val legs = listOf(PermitItineraryLeg("4675323001", LocalDate.of(2026, 7, 12)))
        val request = itineraryRequest(legs, groupSize = 4)
        stubItineraryAvailability(
            "4675323",
            "4675323001",
            7,
            2026,
            // A future/unseen quota type key deserializes to UNKNOWN — remaining=1 must NOT be treated
            // as "one flat slot is enough"; an unrecognized quota's semantics aren't known, so it's
            // held to the stricter PAX-based bar rather than defaulting to CONSTANT's laxer check.
            mapOf(PermitQuotaType.UNKNOWN to mapOf(LocalDate.of(2026, 7, 12) to PermitItineraryAvailabilityCell(remaining = 1))),
        )

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

        assertFalse(result.hasAvailability)
    }

    // --- trailhead matcher ---

    @Test
    fun `trailhead matcher marks available when one of several accepted divisions has remaining quota`() {
        val request = trailheadRequest(divisionIds = listOf("44585905", "44585901"), LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 25))
        stubTrailheadAvailability(
            "445859",
            mapOf(
                LocalDate.of(2026, 7, 21) to mapOf("44585905" to singleGateCell(remaining = 0)),
                LocalDate.of(2026, 7, 22) to mapOf("44585901" to singleGateCell(remaining = 15)),
            ),
        )
        stubTrailheadDivisionAvailability("445859", "44585901", LocalDate.of(2026, 7, 22), singleGateCell(remaining = 15))

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

        assertTrue(result.hasAvailability)
        assertEquals("44585901", result.matchedDivisionId)
        assertEquals(LocalDate.of(2026, 7, 22), result.matchedDate)
    }

    @Test
    fun `trailhead matcher marks unavailable when no accepted division has quota in window`() {
        val request = trailheadRequest(divisionIds = listOf("44585905"), LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 25))
        stubTrailheadAvailability("445859", mapOf(LocalDate.of(2026, 7, 21) to mapOf("44585905" to singleGateCell(remaining = 0))))

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

        assertFalse(result.hasAvailability)
        assertNull(result.matchedDivisionId)
        assertNull(result.matchedDate)
    }

    @Test
    fun `trailhead matcher marks unavailable when remaining quota is nonzero but too small for the group`() {
        val request = trailheadRequest(divisionIds = listOf("44585905"), LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 25), groupSize = 4)
        stubTrailheadAvailability("445859", mapOf(LocalDate.of(2026, 7, 21) to mapOf("44585905" to singleGateCell(remaining = 1))))

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

        assertFalse(result.hasAvailability)
    }

    @Test
    fun `trailhead matcher never matches a not_yet_released cell, regardless of the numbers present`() {
        // Yosemite shape: a rolling-release cell that hasn't opened yet shows remaining=0 alongside the
        // flag in practice, but the flag alone must be decisive even if a future/buggy response paired
        // it with a nonzero remaining.
        val request = trailheadRequest(divisionIds = listOf("44585914"), LocalDate.of(2026, 7, 25), LocalDate.of(2026, 7, 25))
        stubTrailheadAvailability(
            "445859",
            mapOf(
                LocalDate.of(2026, 7, 25) to mapOf(
                    "44585914" to trailheadCell(
                        gates = mapOf("quota_usage_by_member_daily" to PermitTrailheadQuotaGate(total = 15, remaining = 15)),
                        notYetReleased = true,
                    ),
                ),
            ),
        )

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

        assertFalse(result.hasAvailability)
    }

    @Test
    fun `trailhead matcher treats a division absent from a date's response as unavailable`() {
        // Confirmed live (Mt. Whitney): a date entirely absent from the payload, and a division absent
        // from a date that otherwise has other divisions present, are both just "nothing to find" —
        // there's no explicit null-check branch, so this test exercises that it behaves correctly.
        val request = trailheadRequest(divisionIds = listOf("44585901"), LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 22))
        stubTrailheadAvailability(
            "445859",
            mapOf(LocalDate.of(2026, 7, 21) to mapOf("44585905" to singleGateCell(remaining = 25))),
        )

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

        assertFalse(result.hasAvailability)
    }

    @Test
    fun `trailhead matcher requires every quota gate on a cell to clear, not just one`() {
        // Enchantments shape: a flat 1-permit-per-day gate blocks the match even though the per-person
        // gate shows plenty of room.
        val request = trailheadRequest(divisionIds = listOf("445863002"), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1))
        stubTrailheadAvailability(
            "445859",
            mapOf(
                LocalDate.of(2026, 8, 1) to mapOf(
                    "445863002" to trailheadCell(
                        gates = mapOf(
                            "constant_quota_usage_daily" to PermitTrailheadQuotaGate(total = 1, remaining = 0),
                            "quota_usage_by_member_daily" to PermitTrailheadQuotaGate(total = 8, remaining = 8),
                        ),
                    ),
                ),
            ),
        )

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

        assertFalse(result.hasAvailability)
    }

    @Test
    fun `trailhead matcher matches when every quota gate on a cell independently clears`() {
        val request = trailheadRequest(divisionIds = listOf("445863002"), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1))
        val cell = trailheadCell(
            gates = mapOf(
                "constant_quota_usage_daily" to PermitTrailheadQuotaGate(total = 1, remaining = 1),
                "quota_usage_by_member_daily" to PermitTrailheadQuotaGate(total = 8, remaining = 2),
            ),
        )
        stubTrailheadAvailability("445859", mapOf(LocalDate.of(2026, 8, 1) to mapOf("445863002" to cell)))
        stubTrailheadDivisionAvailability("445859", "445863002", LocalDate.of(2026, 8, 1), cell)

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

        assertTrue(result.hasAvailability)
    }

    @Test
    fun `trailhead matcher rejects a candidate match that fails independent corroboration`() {
        val request = trailheadRequest(divisionIds = listOf("44585905"), LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 25))
        stubTrailheadAvailability("445859", mapOf(LocalDate.of(2026, 7, 21) to mapOf("44585905" to singleGateCell(remaining = 15))))
        // The bulk response says 44585905 has room, but the division_id-scoped corroboration call
        // disagrees (remaining=0) — fails closed, same reasoning as the zone matcher's corroboration.
        stubTrailheadDivisionAvailability("445859", "44585905", LocalDate.of(2026, 7, 21), singleGateCell(remaining = 0))

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

        assertFalse(result.hasAvailability)
    }

    @Test
    fun `trailhead matcher skips corroboration when the request is already AVAILABLE (steady state)`() {
        val request = trailheadRequest(divisionIds = listOf("44585905"), LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 25))
        request.state.lastAvailabilityState = AvailabilityState.AVAILABLE
        stubTrailheadAvailability("445859", mapOf(LocalDate.of(2026, 7, 21) to mapOf("44585905" to singleGateCell(remaining = 15))))
        // Deliberately not stubbing getTrailheadDivisionAvailability — if the matcher called it, the
        // unstubbed Call mock would return null and corroboration would fail closed.

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

        assertTrue(result.hasAvailability)
    }

    @Test
    fun `trailhead matcher skips a division flagged suspicious against its baseline`() {
        val request = trailheadRequest(divisionIds = listOf("44585905"), LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 25))
        stubTrailheadAvailability("445859", mapOf(LocalDate.of(2026, 7, 21) to mapOf("44585905" to singleGateCell(remaining = 15))))
        `when`(zoneAvailabilityBaselineService.looksSuspicious(eqK("445859"), eqK("44585905"), anyK(), anyK())).thenReturn(true)

        val result = matcher.check(request, zoneCache, itineraryCache, trailheadCache)

        assertFalse(result.hasAvailability)
    }

    // --- PermitQuotaType JSON parsing (real ObjectMapper config, matching RecreationConfiguration) ---

    @Test
    fun `PermitQuotaType parses known quota type keys and falls back to UNKNOWN for anything unrecognized`() {
        val objectMapper = jacksonObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .registerModule(JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true)

        val json = """
            {
              "payload": {
                "quota_type_maps": {
                  "ConstantQuotaUsageDaily": { "2026-07-12": { "total": 1, "remaining": 1 } },
                  "QuotaUsageByMemberDaily": { "2026-07-12": { "total": 4, "remaining": 3 } },
                  "SomeFutureQuotaTypeRecreationGovAdds": { "2026-07-12": { "total": 1, "remaining": 1 } }
                }
              }
            }
        """.trimIndent()

        val payload = objectMapper.readValue(json, PermitItineraryAvailabilityResponse::class.java).payload

        assertEquals(setOf(PermitQuotaType.ConstantQuotaUsageDaily, PermitQuotaType.QuotaUsageByMemberDaily, PermitQuotaType.UNKNOWN), payload.quotaTypeMaps.keys)
        assertEquals(
            1,
            payload.quotaTypeMaps
                .getValue(PermitQuotaType.ConstantQuotaUsageDaily)
                .getValue(LocalDate.of(2026, 7, 12))
                .remaining
        )
        assertEquals(
            3,
            payload.quotaTypeMaps
                .getValue(PermitQuotaType.QuotaUsageByMemberDaily)
                .getValue(LocalDate.of(2026, 7, 12))
                .remaining
        )
    }

    @Test
    fun `PermitDivisionType parses known division types and falls back to UNKNOWN for anything unrecognized`() {
        val objectMapper = jacksonObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true)

        // "Destination Zone" (Desolation, 233261) and "Camp Area" (Yellowstone, 4675323001) are both
        // confirmed live captures (see openspec/changes/add-permit-search/appendix.md).
        val json = """
            {
              "payload": {
                "divisions": {
                  "343": { "id": "343", "type": "Destination Zone" },
                  "4675323001": { "id": "4675323001", "type": "Camp Area" },
                  "999": { "id": "999", "type": "SomeFutureDivisionTypeRecreationGovAdds" }
                }
              }
            }
        """.trimIndent()

        val divisions = objectMapper.readValue(json, PermitContentResponse::class.java).payload.divisions

        assertEquals(PermitDivisionType.DESTINATION_ZONE, divisions.getValue("343").type)
        assertEquals(PermitDivisionType.CAMP_AREA, divisions.getValue("4675323001").type)
        assertEquals(PermitDivisionType.UNKNOWN, divisions.getValue("999").type)
    }

    @Test
    fun `PermitRuleName parses known rule names and falls back to UNKNOWN for anything unrecognized`() {
        val objectMapper = jacksonObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true)

        // First four rule names are confirmed live captures on the Yellowstone itinerary permit
        // (appendix.md). "QuotaUsageByMember" (no "Daily") is Desolation's zone-permit variant of the
        // same rule — a real, live-verified value this enum deliberately doesn't model, to prove it
        // falls back to UNKNOWN rather than being silently misread as one of the other four.
        val json = """
            {
              "payload": {
                "rules": [
                  { "division_id": "4675323001", "name": "MaxGroupSize", "value": 10 },
                  { "division_id": "4675323001", "name": "StayLimitPerLeg", "value": 259200 },
                  { "division_id": "ALL_DIVISIONS", "name": "ConstantQuotaUsageDaily", "value": 1 },
                  { "division_id": "ALL_DIVISIONS", "name": "QuotaUsageByMemberDaily", "value": 1 },
                  { "division_id": "343", "name": "QuotaUsageByMember", "value": 1 }
                ]
              }
            }
        """.trimIndent()

        val ruleNames = objectMapper
            .readValue(json, PermitContentResponse::class.java)
            .payload.rules
            .map { it.name }

        assertEquals(
            listOf(
                PermitRuleName.MaxGroupSize,
                PermitRuleName.StayLimitPerLeg,
                PermitRuleName.ConstantQuotaUsageDaily,
                PermitRuleName.QuotaUsageByMemberDaily,
                PermitRuleName.UNKNOWN,
            ),
            ruleNames,
        )
    }

    @Test
    fun `SearchEntityType parses known entity types and falls back to UNKNOWN for anything unrecognized`() {
        val objectMapper = jacksonObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true)

        // entity_type "permit" and parent_entity_type "recarea" are the confirmed live capture
        // (appendix.md); the search/suggest endpoint mixes in other inventory kinds too (campgrounds,
        // activity passes) whose exact literal casing isn't confirmed, hence the UNKNOWN fallback case.
        val json = """
            {
              "inventory_suggestions": [
                {
                  "entity_id": "4675323", "entity_type": "permit", "name": "Yellowstone",
                  "parent_entity_id": "2988", "parent_entity_type": "recarea", "parent_name": "Yellowstone National Park"
                },
                { "entity_id": "1234", "entity_type": "someFutureEntityKindRecreationGovAdds", "name": "Unknown Thing" }
              ]
            }
        """.trimIndent()

        val suggestions = objectMapper.readValue(json, SearchSuggestResponse::class.java).inventorySuggestions!!

        assertEquals(SearchEntityType.Permit, suggestions[0].entityType)
        assertEquals(SearchEntityType.Recarea, suggestions[0].parentEntityType)
        assertEquals(SearchEntityType.UNKNOWN, suggestions[1].entityType)
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

        matcher.check(request1, zoneCache, itineraryCache, trailheadCache)
        matcher.check(request2, zoneCache, itineraryCache, trailheadCache)

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
                PermitQuotaType.ConstantQuotaUsageDaily to
                    mapOf(
                        LocalDate.of(2026, 7, 12) to PermitItineraryAvailabilityCell(remaining = 1),
                        LocalDate.of(2026, 7, 20) to PermitItineraryAvailabilityCell(remaining = 1),
                    ),
            ),
        )

        matcher.check(request1, zoneCache, itineraryCache, trailheadCache)
        matcher.check(request2, zoneCache, itineraryCache, trailheadCache)

        org.mockito.Mockito
            .verify(recreationApi, org.mockito.Mockito.times(1))
            .getItineraryDivisionAvailability("4675323", "4675323001", 7, 2026)
    }
}
