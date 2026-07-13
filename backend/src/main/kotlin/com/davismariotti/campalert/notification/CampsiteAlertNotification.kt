package com.davismariotti.campalert.notification

import com.davismariotti.campalert.model.OutboxType
import com.davismariotti.campalert.model.Provider
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.notifications.Notification
import com.davismariotti.notifications.SmsContent

data class PendingNotification(
    val request: SearchRequest,
    val type: OutboxType,
    val outboxId: Long,
)

/** Push content is intentionally not implemented yet — see design D1/D2 (push() defaults to absent). */
class CampsiteAlertNotification(
    private val available: List<PendingNotification>,
    private val gone: List<PendingNotification>,
) : Notification() {
    override fun sms(): SmsContent? {
        val parts = mutableListOf<String>()
        if (available.isNotEmpty()) parts.add(buildAggregatedMessage(available))
        if (gone.isNotEmpty()) parts.add(buildGoneMessage(gone))
        return if (parts.isEmpty()) null else SmsContent(parts.joinToString("\n\n"))
    }

    private fun buildAggregatedMessage(notifications: List<PendingNotification>): String {
        val sb = StringBuilder()
        val count = notifications.size
        if (count > 1) sb.appendLine("$count campgrounds available").appendLine()
        notifications.forEach { n ->
            val endDay = n.request.startDay.plusDays(n.request.nights.toLong())
            sb.appendLine("${n.request.campgroundName} — ${n.request.startDay} to $endDay")
            sb.appendLine(bookingLink(n.request))
            sb.appendLine()
        }
        sb.append("Reply PAUSE to snooze. Reply STOP to unsubscribe.")
        return sb.toString().trimEnd()
    }

    private fun bookingLink(request: SearchRequest): String =
        when (request.provider) {
            Provider.RECREATION_GOV -> "recreation.gov/camping/campgrounds/${request.campsiteId}"
            Provider.CAMPLIFE -> "https://www.camplife.com/${request.campsiteId}/reservation/step1"
        }

    private fun buildGoneMessage(notifications: List<PendingNotification>): String {
        val sb = StringBuilder()
        notifications.forEach { n ->
            val endDay = n.request.startDay.plusDays(n.request.nights.toLong())
            sb.appendLine("${n.request.campgroundName} — ${n.request.startDay} to $endDay is no longer available.")
        }
        sb.append("We'll alert you if it reopens.")
        return sb.toString().trimEnd()
    }
}
