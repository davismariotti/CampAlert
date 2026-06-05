package com.davismariotti.campalert.service.sms

import com.davismariotti.campalert.model.PhoneNumber
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.recreation.Campground
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.service.notification.NotificationService
import com.twilio.rest.api.v2010.account.Message
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import com.twilio.type.PhoneNumber as TwilioPhoneNumber

data class PendingNotification(
    val request: SearchRequest,
    val type: String,
    val outboxId: Long,
)

@Service
class SmsNotificationService(
    private val twilioConfiguration: TwilioConfiguration,
    private val phoneNumberRepository: PhoneNumberRepository,
) : NotificationService {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Legacy single-request path used by Pushover-bypass callers and tests. */
    override fun notify(searchRequest: SearchRequest, campground: Campground, user: User) {
        val verifiedPhones = phoneNumberRepository.findByUserIdAndStatus(user.id!!, PhoneNumberStatus.VERIFIED)
        if (verifiedPhones.isEmpty()) {
            log.warn("No verified phone for user=${user.id}, skipping notification for request=${searchRequest.id}")
            return
        }
        verifiedPhones.forEach { phone ->
            val body = buildLegacyMessage(searchRequest, campground, phone.firstMessageSent)
            sendSms(phone.phone, body)
            if (!phone.firstMessageSent) {
                phoneNumberRepository.save(phone.copy(firstMessageSent = true))
            }
        }
    }

    /**
     * Aggregated outbox path: sends one SMS per verified phone containing all pending notifications.
     * Returns true if SMS was sent, false if no verified phones found.
     */
    fun notifyAggregated(phone: PhoneNumber, notifications: List<PendingNotification>): Boolean {
        val available = notifications.filter { it.type == "AVAILABLE" || it.type == "REMINDER" }
        val gone = notifications.filter { it.type == "UNAVAILABLE" }

        val body = buildCombinedMessage(available, gone)
        sendSms(phone.phone, body)
        return true
    }

    fun buildAggregatedMessage(notifications: List<PendingNotification>): String {
        val sb = StringBuilder()
        val totalSites = notifications.sumOf { it.request.campgroundName.length.coerceAtMost(1) } // count of requests
        val count = notifications.size
        if (count > 1) sb.appendLine("$count campgrounds available").appendLine()
        notifications.forEach { n ->
            val endDay = n.request.startDay.plusDays(n.request.nights.toLong())
            sb.appendLine("${n.request.campgroundName} — ${n.request.startDay} to $endDay")
            sb.appendLine("recreation.gov/camping/campgrounds/${n.request.campsiteId}")
            sb.appendLine()
        }
        sb.append("Reply PAUSE to snooze. Reply STOP to unsubscribe.")
        return sb.toString().trimEnd()
    }

    fun buildGoneMessage(notifications: List<PendingNotification>): String {
        val sb = StringBuilder()
        notifications.forEach { n ->
            val endDay = n.request.startDay.plusDays(n.request.nights.toLong())
            sb.appendLine("${n.request.campgroundName} — ${n.request.startDay} to $endDay is no longer available.")
        }
        sb.append("We'll alert you if it reopens.")
        return sb.toString().trimEnd()
    }

    private fun buildCombinedMessage(available: List<PendingNotification>, gone: List<PendingNotification>): String {
        val parts = mutableListOf<String>()
        if (available.isNotEmpty()) parts.add(buildAggregatedMessage(available))
        if (gone.isNotEmpty()) parts.add(buildGoneMessage(gone))
        return parts.joinToString("\n\n")
    }

    private fun buildLegacyMessage(request: SearchRequest, campground: Campground, firstMessageSent: Boolean): String {
        val endDay = request.startDay.plusDays(request.nights.toLong())
        val sites = campground.campsites.values.joinToString("\n") { "${it.loop} ${it.site}" }
        val body =
            "${request.name} - ${request.startDay} to $endDay\n" +
                "https://www.recreation.gov/camping/campgrounds/${request.campsiteId}\n" +
                sites
        return if (firstMessageSent) body else "$body\n\nCampAlert — Reply STOP to unsubscribe"
    }

    private fun sendSms(toPhone: String, body: String) {
        Message.creator(
            TwilioPhoneNumber(toPhone),
            twilioConfiguration.messagingServiceSid,
            body,
        ).create()
    }
}
