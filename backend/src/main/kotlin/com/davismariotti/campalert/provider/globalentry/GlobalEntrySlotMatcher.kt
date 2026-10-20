package com.davismariotti.campalert.provider.globalentry

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The matching rules for a Global Entry request, as pure date/time arithmetic. Slot timestamps are
 * center-local with no zone and the request's time window is center-local too, so nothing here
 * converts timezones — the caller supplies [now] already expressed in the center's zone.
 */
object GlobalEntrySlotMatcher {
    data class Match(
        val count: Int,
        val earliest: LocalDateTime,
    )

    /**
     * Returns the matching slots' count and earliest, or null if none match. A bookable slot matches when
     * its date is within [firstDay]..[lastDay] inclusive, its start time-of-day is not before
     * [earliestTime] (inclusive) and strictly before [latestTime] (exclusive) — a null bound is open —
     * and it hasn't already started as of [now] (the scheduler keeps reporting past same-day slots as active).
     */
    fun match(
        bookableSlots: Collection<LocalDateTime>,
        firstDay: LocalDate,
        lastDay: LocalDate,
        earliestTime: LocalTime?,
        latestTime: LocalTime?,
        now: LocalDateTime,
    ): Match? {
        val matching = bookableSlots.filter { slot ->
            val day = slot.toLocalDate()
            val time = slot.toLocalTime()
            !day.isBefore(firstDay) &&
                !day.isAfter(lastDay) &&
                (earliestTime == null || !time.isBefore(earliestTime)) &&
                (latestTime == null || time.isBefore(latestTime)) &&
                !slot.isBefore(now)
        }
        if (matching.isEmpty()) return null
        return Match(count = matching.size, earliest = matching.min())
    }
}
