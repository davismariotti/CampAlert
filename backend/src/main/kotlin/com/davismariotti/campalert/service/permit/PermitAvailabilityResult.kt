package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.model.PermitSearchRequest
import java.time.LocalDate

data class PermitAvailabilityResult(
    val request: PermitSearchRequest,
    val hasAvailability: Boolean,
    /** Zone-specific: which accepted division/date currently satisfies the match. */
    val matchedDivisionId: String? = null,
    val matchedDate: LocalDate? = null,
    /** Itinerary-specific: the first leg (in order) found without remaining quota. */
    val blockingDivisionId: String? = null,
    val blockingDate: LocalDate? = null,
)
