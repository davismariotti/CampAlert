package com.davismariotti.campalert.notification

import com.davismariotti.campalert.model.OutboxType
import com.davismariotti.campalert.model.PermitSearchRequest
import com.davismariotti.campalert.model.SearchType
import com.davismariotti.notifications.Notification
import com.davismariotti.notifications.SmsContent

data class PendingPermitNotification(
    val request: PermitSearchRequest,
    val type: OutboxType,
    val outboxId: Long,
    /** Human-readable division name (e.g. "33 Aloha"), resolved via PermitContentCache; falls back to the raw id in the SMS when unavailable. */
    val matchedDivisionName: String? = null,
)

/** Permit analogue of [CampsiteAlertNotification] — built from permit name plus matched zone/date or leg count. */
class PermitAlertNotification(
    private val available: List<PendingPermitNotification>,
    private val gone: List<PendingPermitNotification>,
) : Notification() {
    override fun sms(): SmsContent? {
        val parts = mutableListOf<String>()
        if (available.isNotEmpty()) parts.add(buildAggregatedMessage(available))
        if (gone.isNotEmpty()) parts.add(buildGoneMessage(gone))
        return if (parts.isEmpty()) null else SmsContent(parts.joinToString("\n\n"))
    }

    private fun buildAggregatedMessage(notifications: List<PendingPermitNotification>): String {
        val sb = StringBuilder()
        val count = notifications.size
        if (count > 1) sb.appendLine("$count permits available").appendLine()
        notifications.forEach { n ->
            val summary = matchSummary(n)
            sb.appendLine(n.request.permitName + (summary?.let { " — $it" } ?: ""))
            sb.appendLine("recreation.gov/permits/${n.request.permitId}")
            sb.appendLine()
        }
        sb.append("Reply PAUSE to snooze. Reply STOP to unsubscribe.")
        return sb.toString().trimEnd()
    }

    private fun buildGoneMessage(notifications: List<PendingPermitNotification>): String {
        val sb = StringBuilder()
        notifications.forEach { n -> sb.appendLine("${n.request.permitName} is no longer available.") }
        sb.append("We'll alert you if it reopens.")
        return sb.toString().trimEnd()
    }

    private fun matchSummary(n: PendingPermitNotification): String? {
        val request = n.request
        return when (request.searchType) {
            SearchType.ZONE -> {
                val divisionId = request.state.matchedDivisionId
                val date = request.state.matchedDate
                val divisionLabel = n.matchedDivisionName ?: divisionId
                if (divisionLabel != null && date != null) "zone $divisionLabel on $date" else null
            }
            SearchType.ITINERARY ->
                request.itineraryTarget
                    ?.legs
                    ?.size
                    ?.let { "$it-night itinerary" }
            SearchType.TRAILHEAD -> {
                val divisionId = request.state.matchedDivisionId
                val date = request.state.matchedDate
                val divisionLabel = n.matchedDivisionName ?: divisionId
                if (divisionLabel != null && date != null) "$divisionLabel on $date" else null
            }
        }
    }
}
