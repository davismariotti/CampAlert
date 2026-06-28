package com.davismariotti.campalert.service.notification

import com.davismariotti.campalert.repository.NotificationOutboxRepository
import com.davismariotti.campalert.service.scheduling.UserAvailabilityProcessedEvent
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class CampsiteAlertDispatcher(
    private val outboxProcessor: CampsiteAlertOutboxProcessor,
    private val notificationOutboxRepository: NotificationOutboxRepository,
    @param:Value("\${campfinder.outbox.stale-threshold-minutes:15}")
    private val staleThresholdMinutes: Long,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    private fun onUserProcessed(event: UserAvailabilityProcessedEvent) {
        processOutbox()
    }

    @Scheduled(fixedDelayString = "\${campfinder.notification.safety-net-ms:30000}")
    private fun safetyNet() {
        processOutbox()
    }

    private fun processOutbox() {
        val now = Instant.now()
        val staleThreshold = now.minus(staleThresholdMinutes, ChronoUnit.MINUTES)
        val claimable = notificationOutboxRepository.findClaimable(now, staleThreshold)
        if (claimable.isEmpty()) return
        claimable.groupBy { it.userId }.forEach { (userId, rows) ->
            outboxProcessor.processUser(userId, rows, now)
        }
    }
}
