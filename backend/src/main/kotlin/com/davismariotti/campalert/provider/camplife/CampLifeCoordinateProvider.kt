package com.davismariotti.campalert.provider.camplife

import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.service.availability.CampgroundCoordinateProvider
import org.springframework.stereotype.Service

/** CampLife's coordinate source: the cached global directory, which serves lat/lon as JSON strings, not numbers (verified against real traffic). */
@Service
class CampLifeCoordinateProvider(
    private val campLifeCatalogCache: CampLifeCatalogCache,
) : CampgroundCoordinateProvider {
    override val provider = Provider.CAMPLIFE

    override fun resolveCoordinates(campsiteId: Int): Pair<Double?, Double?> {
        val entry = campLifeCatalogCache.getDirectory().find { it.id == campsiteId }
        return entry?.lat?.toDoubleOrNull() to entry?.lon?.toDoubleOrNull()
    }
}
