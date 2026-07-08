package com.davismariotti.campalert.service.notification

import com.davismariotti.notifications.DispatchResult
import com.davismariotti.notifications.Notification
import com.davismariotti.notifications.NotificationDispatcher
import com.davismariotti.notifications.Recipient
import com.davismariotti.notifications.SendResult
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/**
 * Thin CampAlert-side wrapper around the notifications library's dispatcher. Recipient resolution
 * (verified phone, Pushover targets) is the caller's job — see [RecipientResolver] — this class only
 * dispatches and logs per-channel failures.
 */
@Service
class NotificationService(
    private val dispatcher: NotificationDispatcher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Fire-and-forget: any channel failure is logged; the outcome is not returned to the caller. */
    @Async
    fun sendAsync(notification: Notification, recipient: Recipient) {
        send(notification, recipient)
    }

    /** Synchronous send, returning the per-channel outcome (e.g. for a caller that persists it). */
    fun send(notification: Notification, recipient: Recipient): DispatchResult {
        val result = dispatcher.send(notification, recipient)
        result.byChannel.forEach { (channel, sendResult) ->
            if (sendResult is SendResult.Failure) {
                log.warn(
                    "Notification delivery failed channel={} type={}",
                    channel,
                    notification::class.simpleName,
                    sendResult.cause,
                )
            }
        }
        return result
    }
}
