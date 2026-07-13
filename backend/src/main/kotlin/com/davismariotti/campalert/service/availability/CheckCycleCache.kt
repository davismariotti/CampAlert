package com.davismariotti.campalert.service.availability

/**
 * A per-check-cycle cache a [CampgroundAvailabilityProvider] hands the scheduler via
 * [CampgroundAvailabilityProvider.newCheckCycleCache], and receives back unchanged on every
 * [CampgroundAvailabilityProvider.checkAvailability] call within that same cycle — e.g. deduping
 * repeated fetches for the same date range across every request being processed. The scheduler
 * treats [K]/[V] opaquely (`CheckCycleCache<*, *>`); only the provider that created a given instance
 * knows or casts to its own concrete key/value types.
 */
interface CheckCycleCache<K, V> {
    /** Returns the cached value for [key], invoking [compute] at most once per key within this cache's lifetime (deduped across concurrent callers) if absent. */
    fun computeIfAbsent(key: K, compute: () -> V): V
}
