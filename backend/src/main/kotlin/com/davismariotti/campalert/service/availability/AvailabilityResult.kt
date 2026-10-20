package com.davismariotti.campalert.service.availability

import com.davismariotti.campalert.model.SearchRequest
import java.time.LocalDate
import java.time.LocalTime

data class AvailabilityResult(
    val searchRequest: SearchRequest,
    val hasAvailableSites: Boolean,
    val availableSiteCount: Int,
    val availableSiteIds: Set<String> = emptySet(),
    /** The matched candidate stay's arrival/checkout dates when [hasAvailableSites] is true; null otherwise. */
    val matchedStartDay: LocalDate? = null,
    val matchedEndDay: LocalDate? = null,
    /** Global Entry only: the earliest matching slot's time and the matching slot count; null for other providers and when unavailable. */
    val globalEntryMatch: GlobalEntryMatch? = null,
)

/** What a Global Entry check found, persisted so the delayed alert can name the earliest slot and the count without recomputing. */
data class GlobalEntryMatch(
    val earliestStartTime: LocalTime,
    val slotCount: Int,
)
