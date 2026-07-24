package com.davismariotti.campalert.provider.reservecalifornia

import com.davismariotti.campalert.model.ReserveCaliforniaOccupancyStatus
import com.davismariotti.campalert.model.ReserveCaliforniaUnitOccupancy
import com.davismariotti.campalert.model.ReserveCaliforniaUnitOccupancyId
import com.davismariotti.campalert.provider.CallProtection
import com.davismariotti.campalert.repository.ReserveCaliforniaUnitOccupancyRepository
import com.newrelic.api.agent.NewRelic
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Owns ReserveCalifornia's per-unit occupancy pipeline (design.md D9-D20 / `reserve-california-unit-occupancy-warmup` spec):
 * demand-driven full-roster warm-up for any-site groupSize-scoped searches, on-demand lazy fetch for
 * site_ids-scoped searches, retry/exclusion, and the freshness bookkeeping the reconciliation job
 * (a separate class) relies on. Deliberately does NOT gate poll target registration (D18) — see
 * ReserveCaliforniaAvailabilityProvider for how "unknown occupancy never matches" is enforced instead.
 */
@Service
class ReserveCaliforniaOccupancyService(
    private val repository: ReserveCaliforniaUnitOccupancyRepository,
    private val reserveCaliforniaApi: ReserveCaliforniaApi,
    @Qualifier("reserveCaliforniaCallProtection") private val callProtection: CallProtection,
    @Qualifier("reserveCaliforniaWarmupCallProtection") private val warmupCallProtection: CallProtection,
    @Qualifier("reserveCaliforniaOccupancyExecutor") private val executor: TaskExecutor,
    private val properties: ReserveCaliforniaOccupancyProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Facilities with an in-flight fan-out task in this process (D16's single-instance dedup — cross-instance duplication is accepted as harmless, see D16). */
    private val inFlightFacilities = ConcurrentHashMap.newKeySet<Int>()

    /** D13: seed PENDING rows (only for units not already tracked) and launch the fan-out if not already running for this facility. */
    fun ensureWarmingUp(facilityId: Int, units: List<ReserveCaliforniaRosterUnit>) {
        seedPending(facilityId, units)
        launchFanOutIfNeeded(facilityId)
    }

    fun launchFanOutIfNeeded(facilityId: Int) {
        if (!inFlightFacilities.add(facilityId)) return
        executor.execute {
            try {
                fanOut(facilityId)
            } finally {
                inFlightFacilities.remove(facilityId)
            }
        }
    }

    /** D9/D10: known, sufficient occupancy for an any-site groupSize-scoped search — reads only, never fetches (warm-up is the only thing that populates these). */
    fun findFetchedSufficientFor(facilityId: Int, unitIds: Collection<Int>, groupSize: Int): Set<Int> =
        repository
            .findByIdFacilityIdAndIdUnitIdIn(facilityId, unitIds)
            .filter { satisfiesGroupSize(it, groupSize) }
            .map { it.id.unitId }
            .toSet()

    /** D17: site_ids-scoped resolution — reads whatever's already FETCHED immediately, lazily fetches (through the steady-state call protection, not the warm-up limiter) only its own specific not-yet-FETCHED unit ids. */
    fun resolveSufficientForSiteIds(facilityId: Int, unitIds: Set<Int>, groupSize: Int): Set<Int> {
        val existing = repository.findByIdFacilityIdAndIdUnitIdIn(facilityId, unitIds).associateBy { it.id.unitId }
        val alreadyFetched = existing.filterValues { it.status == ReserveCaliforniaOccupancyStatus.FETCHED }
        val needsFetch = unitIds - alreadyFetched.keys
        val lazilyFetched = needsFetch.mapNotNull { unitId -> fetchOnDemand(facilityId, unitId) }.associateBy { it.id.unitId }
        val combined = alreadyFetched + lazilyFetched
        return combined.values
            .filter { satisfiesGroupSize(it, groupSize) }
            .map { it.id.unitId }
            .toSet()
    }

    private fun satisfiesGroupSize(occupancy: ReserveCaliforniaUnitOccupancy?, groupSize: Int): Boolean {
        if (occupancy == null || occupancy.status != ReserveCaliforniaOccupancyStatus.FETCHED) return false
        val max = occupancy.maxOccupancy ?: return false
        val min = occupancy.minOccupancy ?: 0
        return groupSize in min..max
    }

    private fun seedPending(facilityId: Int, units: List<ReserveCaliforniaRosterUnit>) {
        val existingIds = repository.findByIdFacilityId(facilityId).map { it.id.unitId }.toSet()
        val newRows = units
            .filterNot { it.unitId in existingIds }
            .map { ReserveCaliforniaUnitOccupancy(id = ReserveCaliforniaUnitOccupancyId(facilityId, it.unitId), unitName = it.name) }
        if (newRows.isEmpty()) return
        try {
            repository.saveAll(newRows)
        } catch (e: Exception) {
            // Best-effort: a concurrent seed for the same brand-new facility racing on the same PK is
            // rare and self-healing (the row already exists in the desired state either way).
            log.warn("Failed to seed some ReserveCalifornia occupancy rows facilityId={}", facilityId, e)
        }
    }

    private fun fanOut(facilityId: Int) {
        while (true) {
            val batch = repository.findFetchable(facilityId, Instant.now())
            if (batch.isEmpty()) return
            batch.forEach { row -> fetchAndUpdate(row, warmupCallProtection) }
        }
    }

    /** Skips a real fetch for EXCLUDED rows (terminal until reconciliation) and FAILED rows still inside their cooldown (D15) — the caller correctly treats the unchanged row as a non-match either way. */
    private fun fetchOnDemand(facilityId: Int, unitId: Int): ReserveCaliforniaUnitOccupancy? {
        val id = ReserveCaliforniaUnitOccupancyId(facilityId, unitId)
        val row = repository.findById(id).orElseGet { ReserveCaliforniaUnitOccupancy(id = id, unitName = "") }
        val now = Instant.now()
        val eligible = when (row.status) {
            ReserveCaliforniaOccupancyStatus.FETCHED -> return row
            ReserveCaliforniaOccupancyStatus.EXCLUDED -> false
            ReserveCaliforniaOccupancyStatus.FAILED -> row.nextAttemptAt?.isAfter(now) != true
            ReserveCaliforniaOccupancyStatus.PENDING -> true
        }
        if (!eligible) return row
        return fetchAndUpdate(row, callProtection)
    }

    private fun fetchAndUpdate(row: ReserveCaliforniaUnitOccupancy, protection: CallProtection): ReserveCaliforniaUnitOccupancy? {
        val nightlyUnit = try {
            protection.execute {
                reserveCaliforniaApi
                    .getUnitDetails(row.id.unitId, LocalDate.now().plusDays(1).format(DATE_FORMATTER), 1)
                    .execute()
                    .body()
                    ?.nightlyUnit
            }
        } catch (e: Exception) {
            log.warn("ReserveCalifornia occupancy fetch failed facilityId={} unitId={}", row.id.facilityId, row.id.unitId, e)
            null
        }
        if (nightlyUnit == null) {
            return recordFailure(row)
        }
        val now = Instant.now()
        return repository.save(
            row.copy(
                maxOccupancy = nightlyUnit.maxOccupancy,
                minOccupancy = nightlyUnit.minOccupancy,
                status = ReserveCaliforniaOccupancyStatus.FETCHED,
                fetchedAt = now,
                nextAttemptAt = null,
                nextReconcileAt = now.plus(freshnessWindow()),
            ),
        )
    }

    /** D15: retry with a cooldown up to [ReserveCaliforniaOccupancyProperties.maxAttempts], then permanent exclusion + alert event. */
    private fun recordFailure(row: ReserveCaliforniaUnitOccupancy): ReserveCaliforniaUnitOccupancy {
        val attempts = row.attempts + 1
        val now = Instant.now()
        return if (attempts >= properties.maxAttempts) {
            val excluded = repository.save(
                row.copy(status = ReserveCaliforniaOccupancyStatus.EXCLUDED, attempts = attempts, nextAttemptAt = null, nextReconcileAt = now.plus(freshnessWindow())),
            )
            NewRelic.getAgent().insights.recordCustomEvent(
                "ReserveCaliforniaUnitOccupancyExcluded",
                mapOf("facilityId" to row.id.facilityId, "unitId" to row.id.unitId, "attempts" to attempts),
            )
            excluded
        } else {
            repository.save(
                row.copy(status = ReserveCaliforniaOccupancyStatus.FAILED, attempts = attempts, nextAttemptAt = now.plus(Duration.ofMinutes(properties.retryCooldownMinutes))),
            )
        }
    }

    private fun freshnessWindow(): Duration {
        val jitterDays = if (properties.freshnessJitterDays > 0) Random.nextLong(0, properties.freshnessJitterDays + 1) else 0
        return Duration.ofDays(properties.freshnessDays + jitterDays)
    }

    companion object {
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
