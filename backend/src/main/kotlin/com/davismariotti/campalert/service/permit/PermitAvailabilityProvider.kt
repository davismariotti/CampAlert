package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.model.PermitSearchRequest
import com.davismariotti.campalert.provider.Provider

interface PermitAvailabilityProvider {
    val provider: Provider

    fun check(
        request: PermitSearchRequest,
        zoneCache: ZoneAvailabilityCache,
        itineraryCache: ItineraryAvailabilityCache,
        trailheadCache: TrailheadAvailabilityCache,
    ): PermitAvailabilityResult
}
