package com.davismariotti.campalert.provider.globalentry

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Weekly backstop refresh of the Global Entry directory cache — read-triggered async refresh (see
 * [GlobalEntryCatalogCache.getDirectory]) keeps it fresher in between when there's search traffic.
 * `fixedDelay` fires on every startup, so this only refreshes when due per
 * [GlobalEntryCatalogCache.isDirectoryRefreshDue]; `@SchedulerLock` ensures one instance does it.
 */
@Component
class GlobalEntryCatalogRefreshJob(
    private val globalEntryCatalogCache: GlobalEntryCatalogCache,
    @param:Value($$"${campfinder.globalentry.catalog.refresh-interval-ms:604800000}") private val refreshIntervalMs: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = $$"${campfinder.globalentry.catalog.refresh-interval-ms:604800000}")
    @SchedulerLock(name = "globalEntryCatalogRefresh", lockAtMostFor = "PT10M", lockAtLeastFor = "PT2M")
    fun refresh() {
        if (!globalEntryCatalogCache.isDirectoryRefreshDue(refreshIntervalMs)) {
            log.debug("Global Entry directory refresh skipped; cached value is still within the refresh interval")
            return
        }
        val locations = globalEntryCatalogCache.refreshDirectory()
        if (locations == null) {
            log.warn("Scheduled Global Entry directory refresh failed; previous cached value (if any) left in place")
        } else {
            log.info("Scheduled Global Entry directory refresh populated cache count={}", locations.size)
        }
    }
}
