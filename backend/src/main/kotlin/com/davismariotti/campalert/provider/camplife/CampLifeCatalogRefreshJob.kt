package com.davismariotti.campalert.provider.camplife

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Weekly backstop refresh of the CampLife global directory cache (design.md decision 1) — read-triggered async refresh (see [CampLifeCatalogCache.getDirectory]) keeps the cache fresher in between runs when there's real search traffic. */
@Component
class CampLifeCatalogRefreshJob(
    private val campLifeCatalogCache: CampLifeCatalogCache,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = $$"${campfinder.camplife.catalog.refresh-interval-ms:604800000}")
    fun refresh() {
        val entries = campLifeCatalogCache.refreshDirectory()
        if (entries == null) {
            log.warn("Scheduled CampLife directory refresh failed; previous cached value (if any) left in place")
        } else {
            log.info("Scheduled CampLife directory refresh populated cache count={}", entries.size)
        }
    }
}
