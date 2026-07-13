package com.davismariotti.campalert.service.availability

import com.davismariotti.campalert.model.Provider
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.User

interface CampgroundAvailabilityProvider {
    val provider: Provider

    /**
     * A per-check-cycle cache instance for this provider to reuse across [checkAvailability] calls
     * within one cycle (e.g. deduping repeated fetches for the same date range); `null` if the
     * provider doesn't need one. The scheduler never constructs or inspects a provider-specific type
     * itself — it only creates one via this factory and passes it back unchanged.
     */
    fun newCheckCycleCache(): CheckCycleCache<*, *>? = null

    fun checkAvailability(
        searchRequest: SearchRequest,
        user: User,
        campgroundCache: CheckCycleCache<*, *>? = null,
    ): AvailabilityResult
}
