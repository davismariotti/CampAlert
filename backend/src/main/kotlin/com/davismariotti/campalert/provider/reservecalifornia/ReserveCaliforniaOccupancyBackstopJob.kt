package com.davismariotti.campalert.provider.reservecalifornia

import com.davismariotti.campalert.repository.ReserveCaliforniaUnitOccupancyRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Resumes any facility whose occupancy warm-up was interrupted (e.g. by a restart/deploy) — progress
 * is durable, so this relaunches the fan-out for remaining PENDING/retry-eligible rows rather than
 * starting over (design.md D16). No `@SchedulerLock`: multiple instances relaunching the same
 * facility's remaining work is harmless and idempotent, bounded by the warm-up rate limiter — not
 * worth the coordination cost a lock would add.
 */
@Component
class ReserveCaliforniaOccupancyBackstopJob(
    private val repository: ReserveCaliforniaUnitOccupancyRepository,
    private val occupancyService: ReserveCaliforniaOccupancyService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = $$"${campfinder.reservecalifornia.occupancy.backstop-interval-ms:60000}")
    fun resume() {
        val facilityIds = repository.findFacilityIdsNeedingWarmup(Instant.now())
        if (facilityIds.isEmpty()) return
        log.debug("ReserveCalifornia occupancy backstop resuming facilityCount={}", facilityIds.size)
        facilityIds.forEach { occupancyService.launchFanOutIfNeeded(it) }
    }
}
