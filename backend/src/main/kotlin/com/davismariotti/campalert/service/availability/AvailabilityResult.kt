package com.davismariotti.campalert.service.availability

import com.davismariotti.campalert.model.SearchRequest
import java.time.LocalDate

data class AvailabilityResult(
    val searchRequest: SearchRequest,
    val hasAvailableSites: Boolean,
    val availableSiteCount: Int,
    val availableSiteIds: Set<String> = emptySet(),
    /** The matched candidate stay's arrival/checkout dates when [hasAvailableSites] is true; null otherwise. */
    val matchedStartDay: LocalDate? = null,
    val matchedEndDay: LocalDate? = null,
)
