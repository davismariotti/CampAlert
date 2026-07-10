package com.davismariotti.campalert.service.notification

import com.davismariotti.campalert.model.AlertableRequest
import com.davismariotti.campalert.model.AvailabilityState
import com.davismariotti.campalert.model.NotificationOutbox
import com.davismariotti.campalert.model.OutboxType
import com.davismariotti.campalert.model.PermitSearchRequest
import com.davismariotti.campalert.model.RequestType
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.notification.CampsiteAlertNotification
import com.davismariotti.campalert.notification.PendingNotification
import com.davismariotti.campalert.notification.PendingPermitNotification
import com.davismariotti.campalert.notification.PermitAlertNotification
import com.davismariotti.campalert.repository.NotificationOutboxRepository
import com.davismariotti.campalert.repository.PermitSearchRequestRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.sms.SmsConversationService
import com.davismariotti.notifications.Channel
import com.davismariotti.notifications.Notification
import com.davismariotti.notifications.PushContent
import com.davismariotti.notifications.PushoverSender
import com.davismariotti.notifications.PushoverTarget
import com.davismariotti.notifications.Recipient
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
    private val permitSearchRequestRepository: PermitSearchRequestRepository,
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

        val campgroundById = searchRequestRepository
            .findAllById(rows.filter { it.requestType == RequestType.CAMPGROUND }.map { it.requestId })
            .associateBy { it.id!! }
        val permitById = permitSearchRequestRepository
            .findAllById(rows.filter { it.requestType == RequestType.PERMIT }.map { it.requestId })
            .associateBy { it.id!! }
        val rowById = rows.associateBy { it.id!! }

        // For UNAVAILABLE type, only keep the latest entry per (requestType, requestId); miss superseded ones.
        val latestUnavailableIdByRequest = rows
            .filter { it.type == OutboxType.UNAVAILABLE }
            .groupBy { RequestKey(it.requestType, it.requestId) }
            .mapValues { (_, entries) -> entries.maxBy { it.sendAfter }.id!! }

        val campgroundToSend = mutableListOf<PendingNotification>()
        val permitToSend = mutableListOf<PendingPermitNotification>()

        rows.forEach { row ->
            val request: AlertableRequest? = when (row.requestType) {
                RequestType.CAMPGROUND -> campgroundById[row.requestId]
                RequestType.PERMIT -> permitById[row.requestId]
            }
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
            if (row.type == OutboxType.UNAVAILABLE && row.id != latestUnavailableIdByRequest[RequestKey(row.requestType, row.requestId)]) {
                notificationOutboxRepository.save(row.copy(missedAt = now))
                return@forEach
            }
            when (row.requestType) {
                RequestType.CAMPGROUND -> campgroundToSend.add(PendingNotification(request as SearchRequest, row.type, row.id!!))
                RequestType.PERMIT -> permitToSend.add(PendingPermitNotification(request as PermitSearchRequest, row.type, row.id!!))
            }
        }

        if (campgroundToSend.isEmpty() && permitToSend.isEmpty()) return

        val channel = if (pushoverTarget != null) "PUSHOVER" else "SMS"
        val smsContextIds = mutableListOf<Long>()

        if (campgroundToSend.isNotEmpty()) {
            val available = campgroundToSend.filter { it.type == OutboxType.AVAILABLE || it.type == OutboxType.REMINDER }
            val gone = campgroundToSend.filter { it.type == OutboxType.UNAVAILABLE }
            val notification = CampsiteAlertNotification(available, gone)
            val outboxRows = campgroundToSend.map { rowById[it.outboxId]!! }
            val sent = sendGroup(userId, notification, outboxRows, channel, pushoverTarget, recipient, now, available.size, gone.size)
            if (sent) {
                campgroundToSend.forEach { n ->
                    if (n.type == OutboxType.AVAILABLE || n.type == OutboxType.REMINDER) {
                        n.request.lastNotifiedAt = now
                        searchRequestRepository.save(n.request)
                    }
                }
                smsContextIds += available.map { it.request.id!! }
            }
        }

        if (permitToSend.isNotEmpty()) {
            val available = permitToSend.filter { it.type == OutboxType.AVAILABLE || it.type == OutboxType.REMINDER }
            val gone = permitToSend.filter { it.type == OutboxType.UNAVAILABLE }
            val notification = PermitAlertNotification(available, gone)
            val outboxRows = permitToSend.map { rowById[it.outboxId]!! }
            val sent = sendGroup(userId, notification, outboxRows, channel, pushoverTarget, recipient, now, available.size, gone.size)
            if (sent) {
                permitToSend.forEach { n ->
                    if (n.type == OutboxType.AVAILABLE || n.type == OutboxType.REMINDER) {
                        n.request.lastNotifiedAt = now
                        permitSearchRequestRepository.save(n.request)
                    }
                }
                // No SMS-reply-context wiring for permits yet (SmsWebhookService's PAUSE flow is
                // campground-only) — intentionally not added to smsContextIds.
            }
        }

        if (pushoverTarget == null && smsContextIds.isNotEmpty()) {
            smsConversationService.setContext(recipient.phone!!, smsContextIds)
        }
    }

    /** Sends [notification] and updates [outboxRows]' claim/retry/sent state accordingly; returns whether it was sent. */
    private fun sendGroup(
        userId: Long,
        notification: Notification,
        outboxRows: List<NotificationOutbox>,
        channel: String,
        pushoverTarget: PushoverTarget?,
        recipient: Recipient,
        now: Instant,
        availableCount: Int,
        goneCount: Int,
    ): Boolean {
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
                log.info(
                    "Notification sent userId={} channel={} type={} available={} gone={}",
                    userId,
                    channel,
                    notification::class.simpleName,
                    availableCount,
                    goneCount,
                )
                NewRelic.getAgent().insights.recordCustomEvent(
                    "NotificationSent",
                    mapOf(
                        "userId" to userId,
                        "channel" to channel,
                        "notificationType" to (notification::class.simpleName ?: ""),
                        "availableCount" to availableCount,
                        "goneCount" to goneCount,
                    ),
                )
                outboxRows.forEach { row -> notificationOutboxRepository.save(row.copy(sentAt = now)) }
                return true
            } else {
                val cause = (result as? SendResult.Failure)?.cause
                log.error("Failed to send alert to userId={}", userId, cause)
                outboxRows.forEach { row -> notificationOutboxRepository.save(row.copy(claimedAt = null, attemptCount = row.attemptCount + 1)) }
                return false
            }
        } catch (e: Exception) {
            // Backstop for an exception escaping the SendResult contract (senders are expected to
            // return SendResult.Failure, never throw, for expected delivery failures).
            log.error("Failed to send alert to userId={}", userId, e)
            outboxRows.forEach { row -> notificationOutboxRepository.save(row.copy(claimedAt = null, attemptCount = row.attemptCount + 1)) }
            return false
        }
    }

    private data class RequestKey(
        val requestType: RequestType,
        val requestId: Long
    )
}
