package com.davismariotti.campalert.provider.reservecalifornia

import com.davismariotti.campalert.model.ReserveCaliforniaUnitOccupancy
import com.davismariotti.campalert.model.ReserveCaliforniaUnitOccupancyId
import com.davismariotti.campalert.repository.ReserveCaliforniaUnitOccupancyRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Monthly reconciliation (design.md D20): (a) re-diffs each known facility's roster against a fresh
 * grid call — new units get seeded, removed units get cleaned up — and (b) resets any occupancy row
 * whose jittered 90-day-ish freshness window has elapsed back to PENDING, including permanently
 * excluded units, so they get a fresh chance in case whatever caused their failures has since been
 * fixed. `@SchedulerLock` since multiple app instances could otherwise run this redundantly.
 */
@Component
class ReserveCaliforniaOccupancyReconciliationJob(
    private val repository: ReserveCaliforniaUnitOccupancyRepository,
    private val catalogCache: ReserveCaliforniaCatalogCache,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = $$"${campfinder.reservecalifornia.occupancy.reconciliation-interval-ms:2592000000}")
    @SchedulerLock(name = "reserveCaliforniaOccupancyReconciliation", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    fun reconcile() {
        val now = Instant.now()
        val resetCount = repository.resetStaleRows(now)
        log.info("ReserveCalifornia occupancy reconciliation reset stale rows count={}", resetCount)

        val facilityIds = repository.findKnownFacilityIds()
        facilityIds.forEach { facilityId ->
            try {
                reconcileRoster(facilityId)
            } catch (e: Exception) {
                log.warn("ReserveCalifornia occupancy roster reconciliation failed facilityId={}", facilityId, e)
            }
        }
        log.info("ReserveCalifornia occupancy reconciliation completed facilityCount={}", facilityIds.size)
    }

    private fun reconcileRoster(facilityId: Int) {
        val roster = catalogCache.getFacilityRoster(facilityId) ?: return
        val currentUnitIds = roster.units.map { it.unitId }.toSet()
        val existingUnitIds = repository.findByIdFacilityId(facilityId).map { it.id.unitId }.toSet()

        val newUnits = roster.units.filterNot { it.unitId in existingUnitIds }
        if (newUnits.isNotEmpty()) {
            repository.saveAll(
                newUnits.map { ReserveCaliforniaUnitOccupancy(id = ReserveCaliforniaUnitOccupancyId(facilityId, it.unitId), unitName = it.name) },
            )
        }

        val removedUnitIds = existingUnitIds - currentUnitIds
        if (removedUnitIds.isNotEmpty()) {
            repository.deleteByFacilityIdAndUnitIds(facilityId, removedUnitIds)
        }
    }
}
