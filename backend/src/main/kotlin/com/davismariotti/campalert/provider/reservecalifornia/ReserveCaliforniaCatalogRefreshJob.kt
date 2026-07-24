package com.davismariotti.campalert.provider.reservecalifornia

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Weekly backstop refresh of the ReserveCalifornia directory cache — mirrors
 * CampLifeCatalogRefreshJob. `fixedDelay` scheduling fires immediately on every app startup, so this
 * only actually refreshes when it's due per [ReserveCaliforniaCatalogCache.isDirectoryRefreshDue] —
 * cache empty (first start) or older than the refresh interval — rather than on every deploy.
 * `@SchedulerLock` ensures only one instance does it when several start at once.
 */
@Component
class ReserveCaliforniaCatalogRefreshJob(
    private val reserveCaliforniaCatalogCache: ReserveCaliforniaCatalogCache,
    @param:Value($$"${campfinder.reservecalifornia.catalog.refresh-interval-ms:604800000}") private val refreshIntervalMs: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = $$"${campfinder.reservecalifornia.catalog.refresh-interval-ms:604800000}")
    @SchedulerLock(name = "reserveCaliforniaCatalogRefresh", lockAtMostFor = "PT10M", lockAtLeastFor = "PT2M")
    fun refresh() {
        if (!reserveCaliforniaCatalogCache.isDirectoryRefreshDue(refreshIntervalMs)) {
            log.debug("ReserveCalifornia directory refresh skipped; cached value is still within the refresh interval")
            return
        }
        val entries = reserveCaliforniaCatalogCache.refreshDirectory()
        if (entries == null) {
            log.warn("Scheduled ReserveCalifornia directory refresh failed; previous cached value (if any) left in place")
        } else {
            log.info("Scheduled ReserveCalifornia directory refresh populated cache count={}", entries.size)
        }
    }
}
