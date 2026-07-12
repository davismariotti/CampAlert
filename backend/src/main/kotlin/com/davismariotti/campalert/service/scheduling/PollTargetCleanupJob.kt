package com.davismariotti.campalert.service.scheduling

import com.davismariotti.campalert.repository.PollTargetStateDao
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/** Prunes `poll_target_state` rows for targets with no active request, once they've been stale for a while. Purely a housekeeping job — the dispatcher's claim query already ignores these rows regardless. */
@Component
class PollTargetCleanupJob(
    private val pollTargetStateDao: PollTargetStateDao,
    @param:Value($$"${campfinder.polling.cleanup-threshold-ms:604800000}") private val cleanupThresholdMs: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = $$"${campfinder.polling.cleanup-interval-ms:86400000}")
    fun cleanup() {
        val deleted = pollTargetStateDao.deleteStaleOrphans(Instant.now(), cleanupThresholdMs)
        if (deleted > 0) {
            log.info("Poll target cleanup deleted stale rows count={}", deleted)
        }
    }
}
