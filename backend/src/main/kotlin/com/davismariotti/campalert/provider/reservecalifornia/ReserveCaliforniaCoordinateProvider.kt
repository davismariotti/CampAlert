package com.davismariotti.campalert.provider.reservecalifornia

import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.service.availability.CampgroundCoordinateProvider
import org.springframework.stereotype.Service

/**
 * ReserveCalifornia's coordinate source: the facility's own cached roster coordinates when known
 * (D4 primary source, more precise — verified up to ~0.6mi from the parent park's centroid), falling
 * back to the parent park's coordinates from the cached directory when the facility's roster hasn't
 * been fetched yet.
 */
@Service
class ReserveCaliforniaCoordinateProvider(
    private val catalogCache: ReserveCaliforniaCatalogCache,
) : CampgroundCoordinateProvider {
    override val provider = Provider.RESERVE_CALIFORNIA

    override fun resolveCoordinates(campsiteId: Int): Pair<Double?, Double?> {
        val roster = catalogCache.getFacilityRoster(campsiteId)
        if (roster?.latitude != null && roster.longitude != null) {
            return roster.latitude to roster.longitude
        }
        val entry = catalogCache.getDirectory().find { it.facilityId == campsiteId }
        return entry?.placeLatitude to entry?.placeLongitude
    }
}
