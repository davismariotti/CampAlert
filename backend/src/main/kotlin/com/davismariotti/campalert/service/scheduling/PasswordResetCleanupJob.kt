package com.davismariotti.campalert.service.scheduling

import com.davismariotti.campalert.config.PasswordResetProperties
import com.davismariotti.campalert.repository.PasswordResetRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Purges `password_resets` rows that expired more than [PasswordResetProperties.cleanupRetention]
 * ago, so unconsumed tokens don't accumulate forever. Expiry is fixed at issuance regardless of
 * whether the row was later consumed, so this single check covers both consumed and never-consumed
 * rows without a separate consumed-at condition.
 */
@Component
class PasswordResetCleanupJob(
    private val passwordResetRepository: PasswordResetRepository,
    private val props: PasswordResetProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = $$"${campfinder.email.reset.cleanup-schedule}")
    @SchedulerLock(name = "passwordResetCleanup", lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
    fun purgeExpired() {
        val cutoff = Instant.now().minus(props.cleanupRetention)
        val deleted = passwordResetRepository.deleteExpiredBefore(cutoff)
        if (deleted > 0) {
            log.info("Purged {} expired password_resets row(s) older than {}", deleted, cutoff)
        }
    }
}
