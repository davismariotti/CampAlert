package com.davismariotti.campalert.service

import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.repository.PermitSearchRequestRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import org.springframework.stereotype.Service

/**
 * Counts a user's active (not completed, not soft-deleted) search requests, combined across both
 * campground and permit search requests — the combined quota spans both per specs/user-resource-limits.
 * Only `RECREATION_GOV` permit requests exist (schema-enforced), so per-provider counts for
 * `CAMPLIFE`/`RESERVE_CALIFORNIA` are effectively campground-only, but querying both tables
 * unconditionally keeps this correct without special-casing providers.
 */
@Service
class UserActiveRequestCountService(
    private val searchRequestRepository: SearchRequestRepository,
    private val permitSearchRequestRepository: PermitSearchRequestRepository,
) {
    fun combinedActiveCount(userId: Long): Long = searchRequestRepository.countActiveByUserId(userId) + permitSearchRequestRepository.countActiveByUserId(userId)

    fun providerActiveCount(userId: Long, provider: Provider): Long =
        searchRequestRepository.countActiveByUserIdAndProvider(userId, provider) +
            permitSearchRequestRepository.countActiveByUserIdAndProvider(userId, provider)
}
