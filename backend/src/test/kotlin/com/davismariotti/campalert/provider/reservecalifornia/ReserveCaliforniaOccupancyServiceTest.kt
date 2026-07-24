package com.davismariotti.campalert.provider.reservecalifornia

import com.davismariotti.campalert.model.ReserveCaliforniaOccupancyStatus
import com.davismariotti.campalert.model.ReserveCaliforniaUnitOccupancy
import com.davismariotti.campalert.model.ReserveCaliforniaUnitOccupancyId
import com.davismariotti.campalert.provider.CallProtection
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.repository.ReserveCaliforniaUnitOccupancyRepository
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.springframework.core.task.SyncTaskExecutor
import retrofit2.Call
import retrofit2.Response
import java.time.Duration
import java.time.Instant
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReserveCaliforniaOccupancyServiceTest {
    private val repository = mock(ReserveCaliforniaUnitOccupancyRepository::class.java)
    private val reserveCaliforniaApi = mock(ReserveCaliforniaApi::class.java)
    private val callProtection: CallProtection =
        CallProtection
            .Builder(Provider.RESERVE_CALIFORNIA)
            .circuitBreaker(CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()))
            .retry(RetryRegistry.of(RetryConfig.ofDefaults()))
            .build()
    private val warmupCallProtection: CallProtection =
        CallProtection
            .Builder("reserve-california-warmup-test")
            .circuitBreaker(CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()))
            .retry(RetryRegistry.of(RetryConfig.ofDefaults()))
            .build()
    private val properties = ReserveCaliforniaOccupancyProperties(maxAttempts = 5, retryCooldownMinutes = 10, freshnessDays = 90, freshnessJitterDays = 14)

    private val service = ReserveCaliforniaOccupancyService(
        repository,
        reserveCaliforniaApi,
        callProtection,
        warmupCallProtection,
        SyncTaskExecutor(),
        properties,
    )

    private val facilityId = 585
    private val unitId = 43308

    init {
        `when`(repository.save(any())).thenAnswer { it.arguments[0] }
    }

    private fun row(status: ReserveCaliforniaOccupancyStatus, attempts: Int = 0, nextAttemptAt: Instant? = null): ReserveCaliforniaUnitOccupancy =
        ReserveCaliforniaUnitOccupancy(
            id = ReserveCaliforniaUnitOccupancyId(facilityId, unitId),
            unitName = "Campsite #LO1",
            status = status,
            attempts = attempts,
            nextAttemptAt = nextAttemptAt,
        )

    private fun mockDetailsCall(response: Response<ReserveCaliforniaDetailsResponse>) {
        @Suppress("UNCHECKED_CAST")
        val call = mock(Call::class.java) as Call<ReserveCaliforniaDetailsResponse>
        `when`(call.execute()).thenReturn(response)
        `when`(reserveCaliforniaApi.getUnitDetails(eq(unitId), any(), any())).thenReturn(call)
    }

    @Test
    fun `a successful fetch marks the row FETCHED with occupancy and a future reconcile date`() {
        `when`(repository.findById(ReserveCaliforniaUnitOccupancyId(facilityId, unitId))).thenReturn(Optional.of(row(ReserveCaliforniaOccupancyStatus.PENDING)))
        mockDetailsCall(Response.success(ReserveCaliforniaDetailsResponse(nightlyUnit = ReserveCaliforniaNightlyUnit(unitId = unitId, maxOccupancy = 8, minOccupancy = 1))))

        val result = service.resolveSufficientForSiteIds(facilityId, setOf(unitId), groupSize = 6)

        assertEquals(setOf(unitId), result)
        val captor = argumentCaptor<ReserveCaliforniaUnitOccupancy>()
        verify(repository, times(1)).save(captor.capture())
        val saved = captor.firstValue
        assertEquals(ReserveCaliforniaOccupancyStatus.FETCHED, saved.status)
        assertEquals(8, saved.maxOccupancy)
        assertTrue(saved.nextReconcileAt!!.isAfter(Instant.now().plus(Duration.ofDays(89))))
        assertTrue(saved.nextReconcileAt!!.isBefore(Instant.now().plus(Duration.ofDays(105))))
    }

    @Test
    fun `a failed fetch below max attempts is marked FAILED with a 10-minute cooldown`() {
        `when`(repository.findById(ReserveCaliforniaUnitOccupancyId(facilityId, unitId))).thenReturn(Optional.of(row(ReserveCaliforniaOccupancyStatus.PENDING, attempts = 1)))
        mockDetailsCall(Response.success(ReserveCaliforniaDetailsResponse(nightlyUnit = null)))

        service.resolveSufficientForSiteIds(facilityId, setOf(unitId), groupSize = 6)

        val captor = argumentCaptor<ReserveCaliforniaUnitOccupancy>()
        verify(repository).save(captor.capture())
        val saved = captor.firstValue
        assertEquals(ReserveCaliforniaOccupancyStatus.FAILED, saved.status)
        assertEquals(2, saved.attempts)
        assertTrue(saved.nextAttemptAt!!.isAfter(Instant.now().plus(Duration.ofMinutes(9))))
    }

    @Test
    fun `the 5th failed attempt permanently excludes the unit`() {
        `when`(repository.findById(ReserveCaliforniaUnitOccupancyId(facilityId, unitId))).thenReturn(Optional.of(row(ReserveCaliforniaOccupancyStatus.FAILED, attempts = 4)))
        mockDetailsCall(Response.success(ReserveCaliforniaDetailsResponse(nightlyUnit = null)))

        service.resolveSufficientForSiteIds(facilityId, setOf(unitId), groupSize = 6)

        val captor = argumentCaptor<ReserveCaliforniaUnitOccupancy>()
        verify(repository).save(captor.capture())
        assertEquals(ReserveCaliforniaOccupancyStatus.EXCLUDED, captor.firstValue.status)
        assertEquals(5, captor.firstValue.attempts)
    }

    @Test
    fun `already-FETCHED occupancy is read without an additional API call`() {
        `when`(repository.findByIdFacilityIdAndIdUnitIdIn(facilityId, setOf(unitId)))
            .thenReturn(listOf(row(ReserveCaliforniaOccupancyStatus.FETCHED).copy(maxOccupancy = 8, minOccupancy = 1)))

        val result = service.resolveSufficientForSiteIds(facilityId, setOf(unitId), groupSize = 6)

        assertEquals(setOf(unitId), result)
        verify(reserveCaliforniaApi, never()).getUnitDetails(any(), any(), any())
    }

    @Test
    fun `a permanently excluded unit is never retried`() {
        `when`(repository.findByIdFacilityIdAndIdUnitIdIn(facilityId, setOf(unitId))).thenReturn(listOf(row(ReserveCaliforniaOccupancyStatus.EXCLUDED, attempts = 5)))
        `when`(repository.findById(ReserveCaliforniaUnitOccupancyId(facilityId, unitId))).thenReturn(Optional.of(row(ReserveCaliforniaOccupancyStatus.EXCLUDED, attempts = 5)))

        val result = service.resolveSufficientForSiteIds(facilityId, setOf(unitId), groupSize = 1)

        assertEquals(emptySet(), result)
        verify(reserveCaliforniaApi, never()).getUnitDetails(any(), any(), any())
    }

    @Test
    fun `a FAILED unit still within its retry cooldown is not retried`() {
        val stillCoolingDown = row(ReserveCaliforniaOccupancyStatus.FAILED, attempts = 1, nextAttemptAt = Instant.now().plus(Duration.ofMinutes(5)))
        `when`(repository.findByIdFacilityIdAndIdUnitIdIn(facilityId, setOf(unitId))).thenReturn(listOf(stillCoolingDown))
        `when`(repository.findById(ReserveCaliforniaUnitOccupancyId(facilityId, unitId))).thenReturn(Optional.of(stillCoolingDown))

        val result = service.resolveSufficientForSiteIds(facilityId, setOf(unitId), groupSize = 1)

        assertEquals(emptySet(), result)
        verify(reserveCaliforniaApi, never()).getUnitDetails(any(), any(), any())
    }

    @Test
    fun `findFetchedSufficientFor only matches FETCHED rows whose occupancy range covers the requested group size`() {
        val tooSmall = ReserveCaliforniaUnitOccupancy(ReserveCaliforniaUnitOccupancyId(facilityId, 1), "A", maxOccupancy = 4, minOccupancy = 1, status = ReserveCaliforniaOccupancyStatus.FETCHED)
        val sufficient = ReserveCaliforniaUnitOccupancy(ReserveCaliforniaUnitOccupancyId(facilityId, 2), "B", maxOccupancy = 8, minOccupancy = 1, status = ReserveCaliforniaOccupancyStatus.FETCHED)
        val stillPending = ReserveCaliforniaUnitOccupancy(ReserveCaliforniaUnitOccupancyId(facilityId, 3), "C", status = ReserveCaliforniaOccupancyStatus.PENDING)
        `when`(repository.findByIdFacilityIdAndIdUnitIdIn(facilityId, setOf(1, 2, 3))).thenReturn(listOf(tooSmall, sufficient, stillPending))

        val result = service.findFetchedSufficientFor(facilityId, setOf(1, 2, 3), groupSize = 6)

        assertEquals(setOf(2), result)
    }
}
