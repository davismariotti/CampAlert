package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.model.SearchType
import com.davismariotti.campalert.recreation.PermitContentPayload
import com.davismariotti.campalert.recreation.PermitDivisionContent
import com.davismariotti.campalert.recreation.PermitItineraryAvailabilityCell
import com.davismariotti.campalert.recreation.PermitItineraryAvailabilityPayload
import com.davismariotti.campalert.recreation.PermitItineraryAvailabilityResponse
import com.davismariotti.campalert.recreation.PermitQuotaType
import com.davismariotti.campalert.recreation.PermitRuleContent
import com.davismariotti.campalert.recreation.PermitRuleName
import com.davismariotti.campalert.recreation.PermitZoneAvailabilityCell
import com.davismariotti.campalert.recreation.PermitZoneAvailabilityPayload
import com.davismariotti.campalert.recreation.PermitZoneAvailabilityResponse
import com.davismariotti.campalert.recreation.PermitZoneDivisionAvailability
import com.davismariotti.campalert.recreation.RecreationApi
import com.davismariotti.campalert.service.permit.PermitClassificationService
import com.davismariotti.campalert.service.permit.PermitContentCache
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.web.server.ResponseStatusException
import retrofit2.Call
import retrofit2.Response
import java.time.LocalDate
import java.time.ZonedDateTime

class PermitsDelegateImplTest {
    private val recreationApi = mock(RecreationApi::class.java)
    private val permitClassificationService = mock(PermitClassificationService::class.java)
    private val permitContentCache = mock(PermitContentCache::class.java)
    private val circuitBreakerRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())

    private val delegate = PermitsDelegateImpl(recreationApi, permitClassificationService, permitContentCache, circuitBreakerRegistry)

    @BeforeEach
    fun setUp() {
        circuitBreakerRegistry.circuitBreaker("recreation-gov").reset()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> mockCall(body: T): Call<T> {
        val call = mock(Call::class.java) as Call<T>
        `when`(call.execute()).thenReturn(Response.success(body))
        return call
    }

    // --- getPermit: district / maxGroupSize mapping ---

    @Test
    fun `getPermit maps permit-wide maxGroupSize for a zone-shaped permit`() {
        `when`(permitClassificationService.classify("233261")).thenReturn(SearchType.ZONE)
        `when`(permitContentCache.get("233261")).thenReturn(
            PermitContentPayload(
                id = "233261",
                name = "Desolation Wilderness",
                divisions = mapOf(
                    "343" to PermitDivisionContent(id = "343", name = "33 Aloha", district = "Desolation Wilderness"),
                ),
                rules = listOf(PermitRuleContent(divisionId = "", name = PermitRuleName.MaxGroupSize, value = 12)),
            ),
        )

        val response = delegate.getPermit("233261")

        assertEquals(200, response.statusCode.value())
        assertEquals(12, response.body!!.maxGroupSize)
        assertEquals(
            "Desolation Wilderness",
            response.body!!
                .divisions
                .single()
                .district
        )
        assertNull(
            response.body!!
                .divisions
                .single()
                .maxGroupSize
        )
    }

    @Test
    fun `getPermit maps per-division maxGroupSize and district for an itinerary-shaped permit`() {
        `when`(permitClassificationService.classify("4675323")).thenReturn(SearchType.ITINERARY)
        `when`(permitContentCache.get("4675323")).thenReturn(
            PermitContentPayload(
                id = "4675323",
                name = "Yellowstone Backcountry",
                divisions = mapOf(
                    "4675323001" to PermitDivisionContent(
                        id = "4675323001",
                        name = "1A1 Lower Blacktail Creek",
                        district = "Blacktail/Hellroaring",
                        children = listOf("4675323002"),
                    ),
                ),
                rules = listOf(PermitRuleContent(divisionId = "4675323001", name = PermitRuleName.MaxGroupSize, value = 10)),
            ),
        )

        val response = delegate.getPermit("4675323")

        assertEquals(200, response.statusCode.value())
        assertNull(response.body!!.maxGroupSize)
        val division = response.body!!.divisions.single()
        assertEquals("Blacktail/Hellroaring", division.district)
        assertEquals(10, division.maxGroupSize)
        assertEquals(listOf("4675323002"), division.childDivisionIds)
    }

    // --- getPermitAvailability (zone) ---

    @Test
    fun `getPermitAvailability returns division-date availability grid for a zone permit`() {
        `when`(permitClassificationService.classify("233261")).thenReturn(SearchType.ZONE)
        val call = mockCall(
            PermitZoneAvailabilityResponse(
                PermitZoneAvailabilityPayload(
                    permitId = "233261",
                    availability = mapOf(
                        "343" to PermitZoneDivisionAvailability(
                            divisionId = "343",
                            dateAvailability = mapOf(
                                ZonedDateTime.parse("2026-07-17T00:00:00Z") to PermitZoneAvailabilityCell(total = 25, remaining = 3),
                            ),
                        ),
                    ),
                ),
            ),
        )
        `when`(recreationApi.getZonePermitAvailability("233261", "2026-07-01T00:00:00.000Z")).thenReturn(call)

        val response = delegate.getPermitAvailability("233261", LocalDate.of(2026, 7, 1))

        assertEquals(200, response.statusCode.value())
        val cell = response.body!!
            .divisions
            .getValue("343")
            .getValue("2026-07-17")
        assertEquals(25, cell.total)
        assertEquals(3, cell.remaining)
    }

    @Test
    fun `getPermitAvailability rejects an itinerary permit with type mismatch`() {
        `when`(permitClassificationService.classify("4675323")).thenReturn(SearchType.ITINERARY)

        val response = delegate.getPermitAvailability("4675323", LocalDate.of(2026, 7, 1))

        assertEquals(422, response.statusCode.value())
    }

    @Test
    fun `getPermitAvailability rejects an unsupported permit`() {
        `when`(permitClassificationService.classify("999")).thenReturn(null)

        val response = delegate.getPermitAvailability("999", LocalDate.of(2026, 7, 1))

        assertEquals(422, response.statusCode.value())
    }

    @Test
    fun `getPermitAvailability returns 502 when the circuit breaker is open`() {
        `when`(permitClassificationService.classify("233261")).thenReturn(SearchType.ZONE)
        circuitBreakerRegistry.circuitBreaker("recreation-gov").transitionToOpenState()

        val ex = assertThrows(ResponseStatusException::class.java) {
            delegate.getPermitAvailability("233261", LocalDate.of(2026, 7, 1))
        }
        assertEquals(502, ex.statusCode.value())
    }

    @Test
    fun `getPermitAvailability returns 502 on upstream failure`() {
        `when`(permitClassificationService.classify("233261")).thenReturn(SearchType.ZONE)
        val call = mock(Call::class.java)
        `when`(call.execute()).thenThrow(RuntimeException("network down"))
        @Suppress("UNCHECKED_CAST")
        `when`(recreationApi.getZonePermitAvailability("233261", "2026-07-01T00:00:00.000Z")).thenReturn(call as Call<PermitZoneAvailabilityResponse>)

        val ex = assertThrows(ResponseStatusException::class.java) {
            delegate.getPermitAvailability("233261", LocalDate.of(2026, 7, 1))
        }
        assertEquals(502, ex.statusCode.value())
    }

    // --- getPermitDivisionAvailability (itinerary) ---

    @Test
    fun `getPermitDivisionAvailability collapses quota type maps to the minimum remaining per date`() {
        `when`(permitClassificationService.classify("4675323")).thenReturn(SearchType.ITINERARY)
        val call = mockCall(
            PermitItineraryAvailabilityResponse(
                PermitItineraryAvailabilityPayload(
                    quotaTypeMaps = mapOf(
                        PermitQuotaType.ConstantQuotaUsageDaily to mapOf(
                            LocalDate.of(2026, 7, 12) to PermitItineraryAvailabilityCell(total = 1, remaining = 1),
                        ),
                        PermitQuotaType.QuotaUsageByMemberDaily to mapOf(
                            LocalDate.of(2026, 7, 12) to PermitItineraryAvailabilityCell(total = 4, remaining = 0),
                        ),
                    ),
                ),
            ),
        )
        `when`(recreationApi.getItineraryDivisionAvailability("4675323", "4675323001", 7, 2026)).thenReturn(call)

        val response = delegate.getPermitDivisionAvailability("4675323", "4675323001", 7, 2026)

        assertEquals(200, response.statusCode.value())
        val cell = response.body!!.dates.getValue("2026-07-12")
        assertEquals(0, cell.remaining, "remaining must be the minimum across every concurrently-enforced quota map")
        assertEquals(1, cell.total)
    }

    @Test
    fun `getPermitDivisionAvailability rejects a zone permit with type mismatch`() {
        `when`(permitClassificationService.classify("233261")).thenReturn(SearchType.ZONE)

        val response = delegate.getPermitDivisionAvailability("233261", "343", 7, 2026)

        assertEquals(422, response.statusCode.value())
    }

    @Test
    fun `getPermitDivisionAvailability rejects an unsupported permit`() {
        `when`(permitClassificationService.classify("999")).thenReturn(null)

        val response = delegate.getPermitDivisionAvailability("999", "1", 7, 2026)

        assertEquals(422, response.statusCode.value())
    }

    @Test
    fun `getPermitDivisionAvailability returns 502 when the circuit breaker is open`() {
        `when`(permitClassificationService.classify("4675323")).thenReturn(SearchType.ITINERARY)
        circuitBreakerRegistry.circuitBreaker("recreation-gov").transitionToOpenState()

        val ex = assertThrows(ResponseStatusException::class.java) {
            delegate.getPermitDivisionAvailability("4675323", "4675323001", 7, 2026)
        }
        assertEquals(502, ex.statusCode.value())
    }
}
