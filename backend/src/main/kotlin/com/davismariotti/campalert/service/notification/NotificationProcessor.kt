package com.davismariotti.campalert.service.notification

import com.davismariotti.campalert.model.NotificationOutbox
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.recreation.Campground
import com.davismariotti.campalert.repository.NotificationOutboxRepository
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.sms.PendingNotification
import com.davismariotti.campalert.service.sms.SmsConversationService
import com.davismariotti.campalert.service.sms.SmsNotificationService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class NotificationProcessor(
    private val notificationOutboxRepository: NotificationOutboxRepository,
    private val searchRequestRepository: SearchRequestRepository,
    private val phoneNumberRepository: PhoneNumberRepository,
    private val userRepository: UserRepository,
    private val smsNotificationService: SmsNotificationService,
    private val smsConversationService: SmsConversationService,
    private val pushoverNotificationService: PushoverNotificationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun processUser(userId: Long, rows: List<NotificationOutbox>, now: Instant) {
        val ids = rows.map { it.id!! }
        val claimed = notificationOutboxRepository.claimRows(ids, now)
        if (claimed == 0) return

        val user = userRepository.findById(userId).orElse(null)
        if (user != null && user.pushoverOverrideEnabled && user.pushoverApiToken != null) {
            processPushover(userId, rows, now)
            return
        }

        processSms(userId, rows, now)
    }

    private fun processPushover(userId: Long, rows: List<NotificationOutbox>, now: Instant) {
        rows.forEach { row ->
            val request = searchRequestRepository.findById(row.requestId).orElse(null)
            if (request == null) {
                notificationOutboxRepository.save(row.copy(missedAt = now))
                return@forEach
            }
            if (row.type == "AVAILABLE" || row.type == "REMINDER") {
                if (request.lastAvailabilityState == "UNAVAILABLE") {
                    notificationOutboxRepository.save(row.copy(missedAt = now))
                    return@forEach
                }
            }
            val userEntity = userRepository.findById(userId).orElse(null) ?: return@forEach
            try {
                pushoverNotificationService.notify(request, Campground(emptyMap()), userEntity)
                notificationOutboxRepository.findById(row.id!!).orElse(null)?.let {
                    notificationOutboxRepository.save(it.copy(sentAt = now))
                }
            } catch (e: Exception) {
                log.error("Failed to send Pushover to userId=$userId", e)
                notificationOutboxRepository.findById(row.id!!).orElse(null)?.let {
                    notificationOutboxRepository.save(it.copy(claimedAt = null, attemptCount = it.attemptCount + 1))
                }
            }
        }
    }

    private fun processSms(userId: Long, rows: List<NotificationOutbox>, now: Instant) {
        val phones = phoneNumberRepository.findByUserIdAndStatus(userId, PhoneNumberStatus.VERIFIED)
        if (phones.isEmpty()) {
            markMissed(rows, now)
            return
        }
        val phone = phones.first()

        val toSend = mutableListOf<PendingNotification>()
        rows.forEach { row ->
            val request = searchRequestRepository.findById(row.requestId).orElse(null)
            if (request == null) {
                notificationOutboxRepository.save(row.copy(missedAt = now))
                return@forEach
            }
            if (row.type == "AVAILABLE" || row.type == "REMINDER") {
                if (request.lastAvailabilityState == "UNAVAILABLE") {
                    notificationOutboxRepository.save(row.copy(missedAt = now))
                    return@forEach
                }
            }
            toSend.add(PendingNotification(request = request, type = row.type, outboxId = row.id!!))
        }

        if (toSend.isEmpty()) return

        try {
            smsNotificationService.notifyAggregated(phone, toSend)

            toSend.forEach { n ->
                notificationOutboxRepository.findById(n.outboxId).orElse(null)?.let {
                    notificationOutboxRepository.save(it.copy(sentAt = now))
                }
                if (n.type == "AVAILABLE" || n.type == "REMINDER") {
                    searchRequestRepository.findById(n.request.id!!).orElse(null)?.let {
                        searchRequestRepository.save(it.copy(lastNotifiedAt = now))
                    }
                }
            }

            val contextIds = toSend
                .filter { it.type == "AVAILABLE" || it.type == "REMINDER" }
                .map { it.request.id!! }
            if (contextIds.isNotEmpty()) {
                smsConversationService.setContext(phone.phone, contextIds)
            }
        } catch (e: Exception) {
            log.error("Failed to send SMS to userId=$userId", e)
            rows.forEach { row ->
                notificationOutboxRepository.findById(row.id!!).orElse(null)?.let {
                    notificationOutboxRepository.save(it.copy(claimedAt = null, attemptCount = it.attemptCount + 1))
                }
            }
        }
    }

    private fun markMissed(rows: List<NotificationOutbox>, now: Instant) {
        rows.forEach { row ->
            notificationOutboxRepository.save(row.copy(missedAt = now))
        }
    }
}
