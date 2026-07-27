package com.davismariotti.campalert.service

import com.davismariotti.campalert.model.PauseReason
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.repository.PermitSearchRequestRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Re-derives, from scratch, which of a user's active (not completed, not soft-deleted) search
 * requests — campground and permit combined — are allowed to actually run given their *current*
 * effective quotas/provider-access, and pauses/resumes accordingly. See D4 and
 * specs/user-resource-limits/spec.md: this is called eagerly whenever an admin changes a specific
 * user's group membership or per-user overrides, and defensively from the poll-cycle guard for
 * everything else (e.g. a group/global default value edit).
 *
 * Requests already paused with [PauseReason.NO_VERIFIED_PHONE] are never touched here — that's a
 * separate, unrelated lane owned by [PhoneNumberService] — but they still count as "active" for
 * quota purposes (soft-deleting or resolving the phone issue is what frees the slot, not this).
 *
 * Recomputing the full desired state on every call (rather than incrementally reacting to what
 * changed) is deliberate: it's idempotent and self-correcting regardless of *why* a request is
 * over quota, and naturally implements pause-newest-first/resume-oldest-first as a side effect of
 * "keep the oldest N that fit."
 */
@Service
class ResourceReconciliationService(
    private val searchRequestRepository: SearchRequestRepository,
    private val permitSearchRequestRepository: PermitSearchRequestRepository,
    private val userResourceLimitsService: UserResourceLimitsService,
) {
    private class Governed(
        val provider: Provider,
        val createdAt: Instant,
        val currentPauseReason: String?,
        val apply: (String?) -> Unit,
    )

    @Transactional
    fun reconcileUser(userId: Long) {
        val governed = mutableListOf<Governed>()

        searchRequestRepository
            .findActiveByUserId(userId)
            .filterNot { it.state.pauseReason == PauseReason.NO_VERIFIED_PHONE.name }
            .forEach { r ->
                governed += Governed(r.provider, r.createdAt, r.state.pauseReason) { reason ->
                    if (r.state.pauseReason != reason) {
                        r.state.pauseReason = reason
                        searchRequestRepository.save(r)
                    }
                }
            }

        permitSearchRequestRepository
            .findActiveByUserId(userId)
            .filterNot { it.state.pauseReason == PauseReason.NO_VERIFIED_PHONE.name }
            .forEach { r ->
                governed += Governed(r.provider, r.createdAt, r.state.pauseReason) { reason ->
                    if (r.state.pauseReason != reason) {
                        r.state.pauseReason = reason
                        permitSearchRequestRepository.save(r)
                    }
                }
            }

        if (governed.isEmpty()) return

        // Step 1: provider access — a disallowed provider fully blocks its requests, which then
        // never compete for (or count against) the combined/per-provider quota below.
        val (allowed, disabled) = governed.partition { userResourceLimitsService.isProviderAllowed(userId, it.provider).value }
        disabled.forEach { it.apply(PauseReason.PROVIDER_DISABLED.name) }

        // Step 2: per-provider quota, newest-first pause beyond the limit.
        val withinProviderLimit = mutableListOf<Governed>()
        allowed.groupBy { it.provider }.forEach { (provider, requests) ->
            val limit = userResourceLimitsService.effectiveProviderMaxActive(userId, provider).value
            if (limit == null || requests.size <= limit) {
                withinProviderLimit += requests
            } else {
                val newestFirst = requests.sortedByDescending { it.createdAt }
                newestFirst.take(requests.size - limit).forEach { it.apply(PauseReason.QUOTA_EXCEEDED.name) }
                withinProviderLimit += newestFirst.drop(requests.size - limit)
            }
        }

        // Step 3: combined quota across everything that survived step 2, same newest-first rule.
        val combinedLimit = userResourceLimitsService.effectiveCombinedMaxActive(userId).value
        if (withinProviderLimit.size <= combinedLimit) {
            withinProviderLimit.forEach { it.apply(null) }
        } else {
            val newestFirst = withinProviderLimit.sortedByDescending { it.createdAt }
            newestFirst.take(withinProviderLimit.size - combinedLimit).forEach { it.apply(PauseReason.QUOTA_EXCEEDED.name) }
            newestFirst.drop(withinProviderLimit.size - combinedLimit).forEach { it.apply(null) }
        }
    }
}
