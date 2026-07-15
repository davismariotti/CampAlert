package com.davismariotti.campalert.service.availability

import java.time.LocalDate

/**
 * Shared candidate-arrival-date generation for flexible-window search — every [CampgroundAvailabilityProvider]
 * implementation calls this instead of re-deriving the range math, so a new provider gets the same
 * semantics for free.
 */
object CandidateWindows {
    /**
     * Ordered candidate arrival dates for a `nights`-length stay within `[startDay, searchEndDay]`.
     * When `searchEndDay` is null, returns just `[startDay]` (exact-date search). Otherwise returns
     * every date from `startDay` through `searchEndDay - nights` inclusive, ascending — the latest
     * candidate still checks out on or before `searchEndDay`.
     */
    fun arrivalDates(startDay: LocalDate, nights: Int, searchEndDay: LocalDate?): List<LocalDate> {
        if (searchEndDay == null) return listOf(startDay)
        val lastArrival = searchEndDay.minusDays(nights.toLong())
        if (lastArrival.isBefore(startDay)) return emptyList()
        return generateSequence(startDay) { it.plusDays(1) }
            .takeWhile { !it.isAfter(lastArrival) }
            .toList()
    }
}
