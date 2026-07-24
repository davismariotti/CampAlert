package com.davismariotti.campalert.provider.reservecalifornia

import com.davismariotti.campalert.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.springframework.beans.factory.annotation.Autowired
import retrofit2.Call
import retrofit2.Response

/**
 * [ReserveCaliforniaOccupancyServiceTest] covers the state-machine logic against mocked
 * collaborators. This class instead runs the service against the real Postgres testcontainer
 * [IntegrationTestBase] provisions, exercising the actual warm-up fan-out (a real async task on the
 * real `reserveCaliforniaOccupancyExecutor` bean) and persistence round-trip a mocked repository
 * can't catch — matching design.md D9/D10/D13's requirement that any-site groupSize matching only
 * ever succeeds once real occupancy rows have actually landed in the database.
 */
class ReserveCaliforniaOccupancyServiceIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var service: ReserveCaliforniaOccupancyService

    private val facilityId = 585

    private fun mockUnitDetails(unitId: Int, maxOccupancy: Int, minOccupancy: Int = 1) {
        @Suppress("UNCHECKED_CAST")
        val call = mock(Call::class.java) as Call<ReserveCaliforniaDetailsResponse>
        `when`(call.execute()).thenReturn(
            Response.success(ReserveCaliforniaDetailsResponse(nightlyUnit = ReserveCaliforniaNightlyUnit(unitId, maxOccupancy, minOccupancy))),
        )
        `when`(reserveCaliforniaApi.getUnitDetails(eq(unitId), any(), any())).thenReturn(call)
    }

    @Test
    fun `warm-up fan-out persists real occupancy rows and any-site matching only succeeds once they land`() {
        mockUnitDetails(unitId = 1, maxOccupancy = 8)
        mockUnitDetails(unitId = 2, maxOccupancy = 2)

        // Before warm-up runs, nothing is known yet — a real DB read, not a mocked assumption.
        assertThat(service.findFetchedSufficientFor(facilityId, setOf(1, 2), groupSize = 6)).isEmpty()

        service.ensureWarmingUp(
            facilityId,
            listOf(
                ReserveCaliforniaRosterUnit(unitId = 1, name = "Site 1"),
                ReserveCaliforniaRosterUnit(unitId = 2, name = "Site 2"),
            ),
        )

        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && service.findFetchedSufficientFor(facilityId, setOf(1, 2), groupSize = 6).isEmpty()) {
            Thread.sleep(25)
        }

        // Only unit 1 (maxOccupancy 8) satisfies groupSize 6 — unit 2 (maxOccupancy 2) is fetched too,
        // but real persisted occupancy data correctly excludes it, not just a mocked stub's say-so.
        assertThat(service.findFetchedSufficientFor(facilityId, setOf(1, 2), groupSize = 6)).containsExactly(1)
    }
}
