package com.davismariotti.campalert.provider.camplife

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Weekly backstop refresh of the CampLife global directory cache (design.md decision 1) — read-triggered async
 * refresh (see [CampLifeCatalogCache.getDirectory]) keeps the cache fresher in between runs when there's real
 * search traffic. `fixedDelay` scheduling fires immediately on every app startup, so this only actually refreshes
 * when it's due per [CampLifeCatalogCache.isDirectoryRefreshDue] — cache empty (first start) or older than the
 * refresh interval — rather than on every deploy. `@SchedulerLock` ensures only one instance does it when several
 * start at once.
 */
@Component
class CampLifeCatalogRefreshJob(
    private val campLifeCatalogCache: CampLifeCatalogCache,
    @param:Value($$"${campfinder.camplife.catalog.refresh-interval-ms:604800000}") private val refreshIntervalMs: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = $$"${campfinder.camplife.catalog.refresh-interval-ms:604800000}")
    @SchedulerLock(name = "campLifeCatalogRefresh", lockAtMostFor = "PT10M", lockAtLeastFor = "PT2M")
    fun refresh() {
        if (!campLifeCatalogCache.isDirectoryRefreshDue(refreshIntervalMs)) {
            log.debug("CampLife directory refresh skipped; cached value is still within the refresh interval")
            return
        }
        val entries = campLifeCatalogCache.refreshDirectory()
        if (entries == null) {
            log.warn("Scheduled CampLife directory refresh failed; previous cached value (if any) left in place")
        } else {
            log.info("Scheduled CampLife directory refresh populated cache count={}", entries.size)
        }
    }
}
