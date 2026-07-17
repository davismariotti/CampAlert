package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.model.PermitSearchRequest
import com.davismariotti.campalert.model.PermitSearchRequestState
import com.davismariotti.campalert.model.PermitTrailheadTarget
import com.davismariotti.campalert.model.SearchType
import com.davismariotti.campalert.provider.recreation.PermitContentPayload
import com.davismariotti.campalert.provider.recreation.PermitContentResponse
import com.davismariotti.campalert.provider.recreation.PermitDivisionContent
import com.davismariotti.campalert.provider.recreation.PermitDivisionType
import com.davismariotti.campalert.provider.recreation.PermitMappingPayload
import com.davismariotti.campalert.provider.recreation.PermitMappingResponse
import com.davismariotti.campalert.provider.recreation.PermitTrailheadAvailabilityCell
import com.davismariotti.campalert.provider.recreation.PermitTrailheadAvailabilityResponse
import com.davismariotti.campalert.provider.recreation.PermitTrailheadQuotaGate
import com.davismariotti.campalert.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.kotlin.eq
import org.springframework.beans.factory.annotation.Autowired
import retrofit2.Call
import retrofit2.Response
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * Exercises the real, Spring-wired path from classification through to a matched/unmatched trailhead
 * availability result — [PermitClassificationServiceTest] and [PermitAvailabilityMatcherTest] cover the
 * same logic against mocks; this proves the two real beans (backed by the real Redis testcontainer for
 * both permit content caching and baseline-suspicion state) actually agree end to end.
 */
class PermitTrailheadAvailabilityIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var permitClassificationService: PermitClassificationService

    @Autowired
    private lateinit var permitAvailabilityMatcher: PermitAvailabilityMatcher

    @Suppress("UNCHECKED_CAST")
    private fun <T> successCall(body: T): Call<T> {
        val call = Mockito.mock(Call::class.java) as Call<T>
        Mockito.doReturn(Response.success(body)).`when`(call).execute()
        return call
    }

    private fun stubMapping(landIds: List<String> = emptyList()) {
        val call = successCall(PermitMappingResponse(PermitMappingPayload(landPermitIds = landIds)))
        Mockito.`when`(recreationApi.getPermitMapping()).thenReturn(call)
    }

    private fun stubContent(permitId: String, divisions: Map<String, PermitDivisionContent>) {
        val call = successCall(PermitContentResponse(PermitContentPayload(divisions = divisions)))
        Mockito.`when`(recreationApi.getPermitContent(permitId)).thenReturn(call)
    }

    private fun stubTrailheadAvailability(permitId: String, payload: Map<LocalDate, Map<String, PermitTrailheadAvailabilityCell>>) {
        val call = successCall(PermitTrailheadAvailabilityResponse(payload))
        Mockito
            .`when`(recreationApi.getTrailheadPermitAvailability(eq(permitId), anyString(), anyString(), anyBoolean()))
            .thenReturn(call)
    }

    private fun stubTrailheadDivisionAvailability(
        permitId: String,
        divisionId: String,
        date: LocalDate,
        cell: PermitTrailheadAvailabilityCell
    ) {
        val call = successCall(PermitTrailheadAvailabilityResponse(mapOf(date to mapOf(divisionId to cell))))
        Mockito
            .`when`(recreationApi.getTrailheadDivisionAvailability(eq(permitId), eq(divisionId), anyString(), anyString(), anyBoolean()))
            .thenReturn(call)
    }

    private fun trailheadRequest(
        permitId: String,
        divisionIds: List<String>,
        startDay: LocalDate,
        endDay: LocalDate,
        groupSize: Int
    ): PermitSearchRequest {
        val req = PermitSearchRequest(permitId = permitId, permitName = "Test", groupSize = groupSize, name = "Test", searchType = SearchType.TRAILHEAD)
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

    @Test
    fun `land-flagged entry-point permit classifies as TRAILHEAD and a real match is found end to end`() {
        stubMapping(landIds = listOf("445859"))
        stubContent("445859", mapOf("44585905" to PermitDivisionContent(id = "44585905", name = "Bridalveil Creek", type = PermitDivisionType.ENTRY_POINT)))

        val classified = permitClassificationService.classify("445859")
        assertThat(classified).isEqualTo(SearchType.TRAILHEAD)

        val date = LocalDate.of(2027, 7, 21)
        val cell = PermitTrailheadAvailabilityCell(
            isWalkup = false,
            quotaGates = mapOf("quota_usage_by_member_daily" to PermitTrailheadQuotaGate(total = 25, remaining = 10)),
        )
        stubTrailheadAvailability("445859", mapOf(date to mapOf("44585905" to cell)))
        stubTrailheadDivisionAvailability("445859", "44585905", date, cell)

        val request = trailheadRequest("445859", listOf("44585905"), date.minusDays(1), date.plusDays(1), groupSize = 4)
        val result = permitAvailabilityMatcher.check(request, ConcurrentHashMap(), ConcurrentHashMap(), ConcurrentHashMap())

        assertThat(result.hasAvailability).isTrue()
        assertThat(result.matchedDivisionId).isEqualTo("44585905")
        assertThat(result.matchedDate).isEqualTo(date)
    }

    @Test
    fun `dual quota-gate cell (Enchantments shape) blocks a real match end to end when the flat gate is depleted`() {
        stubMapping(landIds = listOf("445863"))
        stubContent("445863", mapOf("445863002" to PermitDivisionContent(id = "445863002", name = "Core Enchantment Zone", type = PermitDivisionType.ENTRY_POINT)))

        val classified = permitClassificationService.classify("445863")
        assertThat(classified).isEqualTo(SearchType.TRAILHEAD)

        val date = LocalDate.of(2027, 8, 1)
        val cell = PermitTrailheadAvailabilityCell(
            isWalkup = false,
            quotaGates = mapOf(
                "constant_quota_usage_daily" to PermitTrailheadQuotaGate(total = 1, remaining = 0),
                "quota_usage_by_member_daily" to PermitTrailheadQuotaGate(total = 8, remaining = 8),
            ),
        )
        stubTrailheadAvailability("445863", mapOf(date to mapOf("445863002" to cell)))

        val request = trailheadRequest("445863", listOf("445863002"), date.minusDays(1), date.plusDays(1), groupSize = 2)
        val result = permitAvailabilityMatcher.check(request, ConcurrentHashMap(), ConcurrentHashMap(), ConcurrentHashMap())

        assertThat(result.hasAvailability).isFalse()
    }
}
