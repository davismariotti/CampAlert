package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.recreation.PermitContentPayload

sealed interface LegValidationResult {
    data object Valid : LegValidationResult

    data class Invalid(
        val legIndex: Int,
        val divisionId: String
    ) : LegValidationResult
}

/**
 * Validates an itinerary's leg sequence against a permit's division adjacency graph (design decision 4).
 * The first leg may name any division on the permit — nothing precedes it to constrain it. Every leg
 * after that must appear in the immediately preceding leg's `children`.
 */
object ItineraryLegValidator {
    fun validate(content: PermitContentPayload, legDivisionIds: List<String>): LegValidationResult {
        for (i in 1 until legDivisionIds.size) {
            val previousDivision = content.divisions[legDivisionIds[i - 1]]
            val currentDivisionId = legDivisionIds[i]
            if (previousDivision == null || currentDivisionId !in previousDivision.children) {
                return LegValidationResult.Invalid(legIndex = i, divisionId = currentDivisionId)
            }
        }
        return LegValidationResult.Valid
    }
}
