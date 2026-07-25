package com.davismariotti.campalert.provider.reservecalifornia

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Weekly backstop refresh of the ReserveCalifornia directory cache — mirrors
 * CampLifeCatalogRefreshJob. `fixedDelay` scheduling fires immediately on every app startup (Spring's
 * default when only `fixedDelay`/no `initialDelay` is set), so this job doubles as the "check on
 * startup and repair if empty" mechanism: combined with
 * [ReserveCaliforniaCatalogCache.isDirectoryRefreshDue] treating an empty cached directory as always
 * due, an app instance that starts up with a poisoned/empty directory (e.g. from the CDN/WAF-blocked
 * refresh failure documented on [ReserveCaliforniaCatalogCache.refreshDirectory]) attempts a repair
 * immediately rather than waiting out the rest of the weekly interval. `@SchedulerLock` ensures only
 * one instance does it when several start at once.
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
        val repairing = reserveCaliforniaCatalogCache.isDirectoryEmpty()
        if (repairing) {
            log.warn("ReserveCalifornia directory cache is empty (first start, evicted, or previously poisoned by a failed refresh); attempting repair")
        }
        val entries = reserveCaliforniaCatalogCache.refreshDirectory()
        when {
            entries == null && repairing -> log.warn("ReserveCalifornia directory repair attempt failed; cache remains empty until the next scheduled attempt")
            entries == null -> log.warn("Scheduled ReserveCalifornia directory refresh failed; previous cached value (if any) left in place")
            repairing -> log.info("ReserveCalifornia directory cache repaired count={}", entries.size)
            else -> log.info("Scheduled ReserveCalifornia directory refresh populated cache count={}", entries.size)
        }
    }
}
