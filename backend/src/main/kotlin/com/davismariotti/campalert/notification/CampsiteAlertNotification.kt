package com.davismariotti.campalert.notification

import com.davismariotti.campalert.model.OutboxType
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.notifications.Notification
import com.davismariotti.notifications.SmsContent
import java.time.LocalDate

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
            val (startDay, endDay) = n.matchedOrExactDates()
            sb.appendLine("${n.request.campgroundName} — $startDay to $endDay")
            sb.appendLine(n.request.provider.bookingLink(n.request.campsiteId))
            sb.appendLine()
        }
        sb.append("Reply PAUSE to snooze. Reply STOP to unsubscribe.")
        return sb.toString().trimEnd()
    }

    private fun buildGoneMessage(notifications: List<PendingNotification>): String {
        val sb = StringBuilder()
        notifications.forEach { n ->
            // The specific matched dates that disappeared are already cleared from state by the time
            // this is built (AvailabilityStateService nulls them on the same transition that queues
            // this notification), so this describes the configured window, not the vanished match.
            val effectiveEnd = n.request.searchEndDay ?: n.request.startDay.plusDays(n.request.nights.toLong())
            sb.appendLine("${n.request.campgroundName} — ${n.request.startDay} to $effectiveEnd is no longer available.")
        }
        sb.append("We'll alert you if it reopens.")
        return sb.toString().trimEnd()
    }

    /** Matched candidate dates (set on every check that found availability, exact or flexible), falling back to the exact-date stay for state predating matched-date persistence. */
    private fun PendingNotification.matchedOrExactDates(): Pair<LocalDate, LocalDate> {
        val startDay = request.state.matchedStartDay ?: request.startDay
        val endDay = request.state.matchedEndDay ?: request.startDay.plusDays(request.nights.toLong())
        return startDay to endDay
    }
}
