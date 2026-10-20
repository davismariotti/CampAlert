package com.davismariotti.campalert.notification

import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.notifications.Notification
import com.davismariotti.notifications.SmsContent
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Global Entry's alert wording, kept separate from [CampsiteAlertNotification] because nothing in the
 * campsite text applies (campgrounds, stay date ranges, checkout dates derived from `nights`). The
 * earliest matching slot's day/time and the slot count come from the request's persisted Global Entry
 * state; times are center-local and labelled with the center's timezone abbreviation since the
 * recipient may be elsewhere.
 */
class GlobalEntryAlertNotification(
    private val available: List<PendingNotification>,
    private val gone: List<PendingNotification>,
) : Notification() {
    override fun sms(): SmsContent? {
        val parts = mutableListOf<String>()
        if (available.isNotEmpty()) parts.add(buildAvailableMessage(available))
        if (gone.isNotEmpty()) parts.add(buildGoneMessage(gone))
        return if (parts.isEmpty()) null else SmsContent(parts.joinToString("\n\n"))
    }

    private fun buildAvailableMessage(notifications: List<PendingNotification>): String {
        val sb = StringBuilder()
        if (notifications.size > 1) sb.appendLine("${notifications.size} Global Entry centers have appointments").appendLine()
        notifications.forEach { n ->
            sb.appendLine("${n.request.campgroundName} — ${availabilitySummary(n.request)}")
            sb.appendLine(n.request.provider.bookingLink(n.request))
            sb.appendLine()
        }
        sb.append("Reply PAUSE to snooze. Reply STOP to unsubscribe.")
        return sb.toString().trimEnd()
    }

    private fun buildGoneMessage(notifications: List<PendingNotification>): String {
        val sb = StringBuilder()
        notifications.forEach { n ->
            sb.appendLine("${n.request.campgroundName} — Global Entry appointments are no longer available for your dates.")
        }
        sb.append("We'll alert you if they reopen.")
        return sb.toString().trimEnd()
    }

    /** "3 appointments available, earliest Tue Oct 27 at 11:40 AM PT", falling back to the watched day range if the match details weren't persisted. */
    private fun availabilitySummary(request: SearchRequest): String {
        val day = request.state.matchedStartDay
        val time = request.globalEntryState?.matchedStartTime
        val count = request.globalEntryState?.matchedSlotCount
        if (day == null || time == null || count == null) {
            return "appointments available ${watchedRange(request)}"
        }
        val noun = if (count == 1) "appointment" else "appointments"
        val zoneLabel = timezoneAbbreviation(request.campgroundTimezone)?.let { " $it" } ?: ""
        return "$count $noun available, earliest ${DAY_FORMAT.format(day)} at ${formatTime(time)}$zoneLabel"
    }

    private fun watchedRange(request: SearchRequest): String {
        val last: LocalDate = request.latestStartDay ?: request.startDay
        return if (last == request.startDay) {
            SHORT_DAY_FORMAT.format(request.startDay)
        } else {
            "${SHORT_DAY_FORMAT.format(request.startDay)} – ${SHORT_DAY_FORMAT.format(last)}"
        }
    }

    private fun formatTime(time: LocalTime): String = TIME_FORMAT.format(time)

    companion object {
        private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE MMM d", Locale.US)
        private val SHORT_DAY_FORMAT = DateTimeFormatter.ofPattern("MMM d", Locale.US)
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

        /** The generic (non-DST-specific) short zone name, e.g. `PT` / `ET`; null when the zone is unknown or has no short form. */
        internal fun timezoneAbbreviation(timezone: String?): String? {
            val zone = timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: return null
            val abbreviation = zone.getDisplayName(TextStyle.SHORT, Locale.US)
            return abbreviation.takeIf { it.isNotBlank() && !it.contains('/') }
        }
    }
}
