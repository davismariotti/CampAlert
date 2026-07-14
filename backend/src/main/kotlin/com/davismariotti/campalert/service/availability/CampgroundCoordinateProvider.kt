package com.davismariotti.campalert.service.availability

import com.davismariotti.campalert.provider.Provider

/**
 * A provider's own campground coordinate lookup, used by [com.davismariotti.campalert.service.TimezoneResolutionService]
 * to resolve a search request's timezone. Implementations propagate failures (including a tripped
 * circuit breaker) to the caller rather than handling them locally — the caller logs and no-ops on
 * any exception, the same way for every provider.
 */
interface CampgroundCoordinateProvider {
    val provider: Provider

    /** [campsiteId] is actually the campground/facility id (Recreation.gov's own confusing terminology — see design.md decision 4's naming note). */
    fun resolveCoordinates(campsiteId: Int): Pair<Double?, Double?>
}
