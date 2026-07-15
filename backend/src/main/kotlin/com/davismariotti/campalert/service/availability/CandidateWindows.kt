package com.davismariotti.campalert.service.availability

import java.time.LocalDate

/**
 * Shared candidate-arrival-date generation for flexible-window search — every [CampgroundAvailabilityProvider]
 * implementation calls this instead of re-deriving the range math, so a new provider gets the same
 * semantics for free.
 */
object CandidateWindows {
    /**
     * Ordered candidate arrival dates within `[startDay, latestStartDay]`. When `latestStartDay` is
     * null, returns just `[startDay]` (exact-date search). `nights` plays no part here — each
     * candidate's own checkout is `candidate + nights`, computed separately by the caller when
     * matching; this only enumerates possible arrival dates.
     */
    fun arrivalDates(startDay: LocalDate, latestStartDay: LocalDate?): List<LocalDate> {
        if (latestStartDay == null) return listOf(startDay)
        if (latestStartDay.isBefore(startDay)) return emptyList()
        return generateSequence(startDay) { it.plusDays(1) }
            .takeWhile { !it.isAfter(latestStartDay) }
            .toList()
    }
}
