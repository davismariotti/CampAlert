package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.model.PermitItineraryLeg
import com.davismariotti.campalert.model.PermitItineraryTarget
import com.davismariotti.campalert.model.PermitSearchRequest
import com.davismariotti.campalert.model.PermitSearchRequestState
import com.davismariotti.campalert.model.PermitZoneTarget
import com.davismariotti.campalert.model.SearchType
import com.davismariotti.campalert.provider.CallProtection
import com.davismariotti.campalert.provider.recreation.PermitContentResponse
import com.davismariotti.campalert.provider.recreation.PermitDivisionType
import com.davismariotti.campalert.provider.recreation.PermitItineraryAvailabilityCell
import com.davismariotti.campalert.provider.recreation.PermitItineraryAvailabilityPayload
import com.davismariotti.campalert.provider.recreation.PermitItineraryAvailabilityResponse
import com.davismariotti.campalert.provider.recreation.PermitQuotaType
import com.davismariotti.campalert.provider.recreation.PermitRuleName
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
        .Builder("recreation-gov")
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
    fun `zone matcher marks unavailable when remaining quota is nonzero but too small for the group`() {
        // Mirrors a real Desolation Wilderness capture: total=25, remaining=1 on a date, but the
        // request's group is 4 — one PAX slot left can't seat the whole group.
        val request = zoneRequest(divisionIds = listOf("343"), LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 20), groupSize = 4)
        stubZoneAvailability(
            "233261",
            mapOf("343" to PermitZoneDivisionAvailability("343", mapOf(LocalDate.of(2026, 7, 17).atStartOfDay(ZoneOffset.UTC) to zoneCell(1)))),
        )

        val result = matcher.check(request, zoneCache, itineraryCache)

        assertFalse(result.hasAvailability)
    }

    @Test
    fun `zone matcher marks available when remaining quota exactly fits the group`() {
        val request = zoneRequest(divisionIds = listOf("343"), LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 20), groupSize = 4)
        stubZoneAvailability(
            "233261",
            mapOf("343" to PermitZoneDivisionAvailability("343", mapOf(LocalDate.of(2026, 7, 17).atStartOfDay(ZoneOffset.UTC) to zoneCell(4)))),
        )

        val result = matcher.check(request, zoneCache, itineraryCache)

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
            mapOf(PermitQuotaType.ConstantQuotaUsageDaily to mapOf(LocalDate.of(2026, 7, 12) to PermitItineraryAvailabilityCell(remaining = 1))),
        )
        stubItineraryAvailability(
            "4675323",
            "4675323002",
            7,
            2026,
            mapOf(PermitQuotaType.ConstantQuotaUsageDaily to mapOf(LocalDate.of(2026, 7, 13) to PermitItineraryAvailabilityCell(remaining = 1))),
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
            mapOf(PermitQuotaType.ConstantQuotaUsageDaily to mapOf(LocalDate.of(2026, 7, 12) to PermitItineraryAvailabilityCell(remaining = 0))),
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
                PermitQuotaType.ConstantQuotaUsageDaily to mapOf(LocalDate.of(2026, 7, 12) to PermitItineraryAvailabilityCell(remaining = 1)),
                PermitQuotaType.QuotaUsageByMemberDaily to mapOf(LocalDate.of(2026, 7, 12) to PermitItineraryAvailabilityCell(remaining = 0)),
            ),
        )

        val result = matcher.check(request, zoneCache, itineraryCache)

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

        val result = matcher.check(request, zoneCache, itineraryCache)

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

        val result = matcher.check(request, zoneCache, itineraryCache)

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

        val result = matcher.check(request, zoneCache, itineraryCache)

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
                PermitQuotaType.ConstantQuotaUsageDaily to
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
