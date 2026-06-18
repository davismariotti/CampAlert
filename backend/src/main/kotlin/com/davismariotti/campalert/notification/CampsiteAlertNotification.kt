package com.davismariotti.campalert.notification

import com.davismariotti.campalert.model.OutboxType
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.User
import java.util.Optional

data class PendingNotification(
    val request: SearchRequest,
    val type: OutboxType,
    val outboxId: Long,
)

class CampsiteAlertNotification(
    user: User,
    private val available: List<PendingNotification>,
    private val gone: List<PendingNotification>,
) : Notification(user) {
    override fun getSmsContent(): Optional<String> {
        val parts = mutableListOf<String>()
        if (available.isNotEmpty()) parts.add(buildAggregatedMessage(available))
        if (gone.isNotEmpty()) parts.add(buildGoneMessage(gone))
        return if (parts.isEmpty()) Optional.empty() else Optional.of(parts.joinToString("\n\n"))
    }

    private fun buildAggregatedMessage(notifications: List<PendingNotification>): String {
        val sb = StringBuilder()
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
