package com.davismariotti.campalert.service.notification

import com.davismariotti.campalert.model.AvailabilityState
import com.davismariotti.campalert.model.NotificationOutbox
import com.davismariotti.campalert.model.OutboxType
import com.davismariotti.campalert.model.RequestType
import com.davismariotti.campalert.notification.CampsiteAlertNotification
import com.davismariotti.campalert.notification.PendingNotification
import com.davismariotti.campalert.repository.NotificationOutboxRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.sms.SmsConversationService
import com.davismariotti.notifications.Channel
import com.davismariotti.notifications.PushContent
import com.davismariotti.notifications.PushoverSender
import com.davismariotti.notifications.PushoverTarget
import com.davismariotti.notifications.SendResult
import com.newrelic.api.agent.NewRelic
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class CampsiteAlertOutboxProcessor(
    private val notificationService: NotificationService,
    private val recipientResolver: RecipientResolver,
    private val pushoverSender: PushoverSender,
    private val notificationOutboxRepository: NotificationOutboxRepository,
    private val searchRequestRepository: SearchRequestRepository,
    private val userRepository: UserRepository,
    private val smsConversationService: SmsConversationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun processUser(userId: Long, rows: List<NotificationOutbox>, now: Instant) {
        val ids = rows.map { it.id!! }
        val claimed = notificationOutboxRepository.claimRows(ids, now)
        if (claimed == 0) return

        val user = userRepository.findById(userId).orElse(null) ?: return

        val recipient = recipientResolver.resolve(user)
        val pushoverTarget = recipient.pushTargets.filterIsInstance<PushoverTarget>().firstOrNull()

        if (pushoverTarget == null && recipient.phone == null) {
            rows.forEach { row -> notificationOutboxRepository.save(row.copy(missedAt = now)) }
            return
        }

        // Only CAMPGROUND rows are resolvable today; PERMIT rows fall through to the
        // "request == null" branch below until section 7 wires a permit repository/builder in.
        val campgroundIds = rows.filter { it.requestType == RequestType.CAMPGROUND }.map { it.requestId }
        val requestByKey = searchRequestRepository
            .findAllById(campgroundIds)
            .associateBy { RequestKey(RequestType.CAMPGROUND, it.id!!) }
        val rowById = rows.associateBy { it.id!! }

        // For UNAVAILABLE type, only keep the latest entry per (requestType, requestId); miss superseded ones.
        val latestUnavailableIdByRequest = rows
            .filter { it.type == OutboxType.UNAVAILABLE }
            .groupBy { RequestKey(it.requestType, it.requestId) }
            .mapValues { (_, entries) -> entries.maxBy { it.sendAfter }.id!! }

        val toSend = mutableListOf<PendingNotification>()
        rows.forEach { row ->
            val key = RequestKey(row.requestType, row.requestId)
            val request = requestByKey[key]
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
            if (row.type == OutboxType.UNAVAILABLE && row.id != latestUnavailableIdByRequest[key]) {
                notificationOutboxRepository.save(row.copy(missedAt = now))
                return@forEach
            }
            toSend.add(PendingNotification(request = request, type = row.type, outboxId = row.id!!))
        }

        if (toSend.isEmpty()) return

        val available = toSend.filter { it.type == OutboxType.AVAILABLE || it.type == OutboxType.REMINDER }
        val gone = toSend.filter { it.type == OutboxType.UNAVAILABLE }
        val notification = CampsiteAlertNotification(available, gone)

        val channel = if (pushoverTarget != null) "PUSHOVER" else "SMS"

        try {
            // Pushover admin override: reuse the SMS-intended text via the free-form Pushover sender,
            // bypassing the structured SMS channel entirely (mutually exclusive with SMS, not additive).
            val result = if (pushoverTarget != null) {
                val smsContent = notification.sms()
                if (smsContent != null) pushoverSender.send(pushoverTarget, PushContent(body = smsContent.text)) else null
            } else {
                notificationService.send(notification, recipient)[Channel.SMS]
            }

            if (result?.isSuccess == true) {
                log.info("Notification sent userId={} channel={} available={} gone={}", userId, channel, available.size, gone.size)
                NewRelic.getAgent().insights.recordCustomEvent(
                    "NotificationSent",
                    mapOf(
                        "userId" to userId,
                        "channel" to channel,
                        "availableCount" to available.size,
                        "goneCount" to gone.size,
                    ),
                )
                toSend.forEach { n ->
                    notificationOutboxRepository.save(rowById[n.outboxId]!!.copy(sentAt = now))
                    if (n.type == OutboxType.AVAILABLE || n.type == OutboxType.REMINDER) {
                        n.request.lastNotifiedAt = now
                        searchRequestRepository.save(n.request)
                    }
                }
                if (pushoverTarget == null) {
                    val contextIds = toSend
                        .filter { it.type == OutboxType.AVAILABLE || it.type == OutboxType.REMINDER }
                        .map { it.request.id!! }
                    if (contextIds.isNotEmpty()) {
                        smsConversationService.setContext(recipient.phone!!, contextIds)
                    }
                }
            } else {
                val cause = (result as? SendResult.Failure)?.cause
                log.error("Failed to send campsite alert to userId={}", userId, cause)
                rows.forEach { row ->
                    notificationOutboxRepository.save(row.copy(claimedAt = null, attemptCount = row.attemptCount + 1))
                }
            }
        } catch (e: Exception) {
            // Backstop for an exception escaping the SendResult contract (senders are expected to
            // return SendResult.Failure, never throw, for expected delivery failures).
            log.error("Failed to send campsite alert to userId={}", userId, e)
            rows.forEach { row ->
                notificationOutboxRepository.save(row.copy(claimedAt = null, attemptCount = row.attemptCount + 1))
            }
        }
    }

    private data class RequestKey(
        val requestType: RequestType,
        val requestId: Long
    )
}
