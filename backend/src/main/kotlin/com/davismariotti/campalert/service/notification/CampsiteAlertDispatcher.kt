package com.davismariotti.campalert.service.notification

import com.davismariotti.campalert.model.AvailabilityState
import com.davismariotti.campalert.model.NotificationOutbox
import com.davismariotti.campalert.model.OutboxType
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.notification.CampsiteAlertNotification
import com.davismariotti.campalert.notification.PendingNotification
import com.davismariotti.campalert.repository.NotificationOutboxRepository
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.scheduling.UserAvailabilityProcessedEvent
import com.davismariotti.campalert.service.sms.SmsConversationService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class CampsiteAlertDispatcher(
    private val notificationService: NotificationService,
    private val notificationOutboxRepository: NotificationOutboxRepository,
    private val searchRequestRepository: SearchRequestRepository,
    private val userRepository: UserRepository,
    private val phoneNumberRepository: PhoneNumberRepository,
    private val smsConversationService: SmsConversationService,
    @param:Value("\${campfinder.outbox.stale-threshold-minutes:15}")
    private val staleThresholdMinutes: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

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
            processUser(userId, rows, now)
        }
    }

    private fun processUser(userId: Long, rows: List<NotificationOutbox>, now: Instant) {
        val ids = rows.map { it.id!! }
        val claimed = notificationOutboxRepository.claimRows(ids, now)
        if (claimed == 0) return

        val user = userRepository.findById(userId).orElse(null) ?: return

        val usePushover = user.pushoverOverrideEnabled && user.pushoverApiToken != null && user.pushoverUserKey != null
        val smsPhone = if (usePushover) {
            null
        } else {
            val phones = phoneNumberRepository.findByUserIdAndStatus(userId, PhoneNumberStatus.VERIFIED)
            if (phones.isEmpty()) {
                rows.forEach { row -> notificationOutboxRepository.save(row.copy(missedAt = now)) }
                return
            }
            phones.first()
        }

        val requestById = searchRequestRepository.findAllById(rows.map { it.requestId }).associateBy { it.id!! }
        val rowById = rows.associateBy { it.id!! }
        val toSend = mutableListOf<PendingNotification>()
        rows.forEach { row ->
            val request = requestById[row.requestId]
            if (request == null) {
                notificationOutboxRepository.save(row.copy(missedAt = now))
                return@forEach
            }
            if ((row.type == OutboxType.AVAILABLE || row.type == OutboxType.REMINDER) &&
                request.lastAvailabilityState == AvailabilityState.UNAVAILABLE
            ) {
                notificationOutboxRepository.save(row.copy(missedAt = now))
                return@forEach
            }
            toSend.add(PendingNotification(request = request, type = row.type, outboxId = row.id!!))
        }

        if (toSend.isEmpty()) return

        val available = toSend.filter { it.type == OutboxType.AVAILABLE || it.type == OutboxType.REMINDER }
        val gone = toSend.filter { it.type == OutboxType.UNAVAILABLE }
        val notification = CampsiteAlertNotification(user, available, gone)

        try {
            val usedPhone = notificationService.send(notification)
            toSend.forEach { n ->
                notificationOutboxRepository.save(rowById[n.outboxId]!!.copy(sentAt = now))
                if (n.type == OutboxType.AVAILABLE || n.type == OutboxType.REMINDER) {
                    searchRequestRepository.save(n.request.copy(lastNotifiedAt = now))
                }
            }
            if (usedPhone != null) {
                val contextIds = toSend
                    .filter { it.type == OutboxType.AVAILABLE || it.type == OutboxType.REMINDER }
                    .map { it.request.id!! }
                if (contextIds.isNotEmpty()) {
                    smsConversationService.setContext(usedPhone.phone, contextIds)
                }
            }
        } catch (e: Exception) {
            log.error("Failed to send campsite alert to userId=$userId", e)
            rows.forEach { row ->
                notificationOutboxRepository.save(row.copy(claimedAt = null, attemptCount = row.attemptCount + 1))
            }
        }
    }
}
