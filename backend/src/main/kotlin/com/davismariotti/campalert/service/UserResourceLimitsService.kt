package com.davismariotti.campalert.service

import com.davismariotti.campalert.model.GlobalQuotaDefault
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.repository.GlobalProviderAccessRepository
import com.davismariotti.campalert.repository.GlobalProviderQuotaDefaultRepository
import com.davismariotti.campalert.repository.GlobalQuotaDefaultRepository
import com.davismariotti.campalert.repository.GroupMemberRepository
import com.davismariotti.campalert.repository.GroupProviderAccessRepository
import com.davismariotti.campalert.repository.GroupProviderQuotaDefaultRepository
import com.davismariotti.campalert.repository.GroupQuotaDefaultRepository
import com.davismariotti.campalert.repository.UserProviderAccessRepository
import com.davismariotti.campalert.repository.UserProviderQuotaOverrideRepository
import com.davismariotti.campalert.repository.UserQuotaOverrideRepository
import org.springframework.stereotype.Service

/**
 * Resolves a user's effective active-search-request quotas and provider access through the
 * three-level fallback described in specs/user-resource-limits/spec.md: per-user override, then
 * a union across the user's groups (most-generous-wins for numeric caps, any-grants-wins for
 * access), then the global default. A group or user row's mere *absence* means "no opinion at
 * this level" and falls through; it never implies "unlimited" or "denied" on its own.
 */
@Service
class UserResourceLimitsService(
    private val groupMemberRepository: GroupMemberRepository,
    private val userQuotaOverrideRepository: UserQuotaOverrideRepository,
    private val userProviderQuotaOverrideRepository: UserProviderQuotaOverrideRepository,
    private val userProviderAccessRepository: UserProviderAccessRepository,
    private val groupQuotaDefaultRepository: GroupQuotaDefaultRepository,
    private val groupProviderQuotaDefaultRepository: GroupProviderQuotaDefaultRepository,
    private val groupProviderAccessRepository: GroupProviderAccessRepository,
    private val globalQuotaDefaultRepository: GlobalQuotaDefaultRepository,
    private val globalProviderQuotaDefaultRepository: GlobalProviderQuotaDefaultRepository,
    private val globalProviderAccessRepository: GlobalProviderAccessRepository,
) {
    /** Source of an effective value, surfaced to the admin UI so it can show *why* a limit applies. */
    enum class Source { USER_OVERRIDE, GROUP_DEFAULT, GLOBAL_DEFAULT }

    data class EffectiveValue<T>(
        val value: T,
        val source: Source
    )

    companion object {
        /** Used only if the `global_quota_defaults` singleton row is somehow missing (GroupSeeder normally guarantees it). */
        private const val FALLBACK_GLOBAL_MAX_ACTIVE = 5
    }

    fun effectiveCombinedMaxActive(userId: Long): EffectiveValue<Int> {
        userQuotaOverrideRepository.findById(userId).orElse(null)?.let {
            return EffectiveValue(it.maxActive, Source.USER_OVERRIDE)
        }
        val groupIds = groupIdsFor(userId)
        val groupMax = groupQuotaDefaultRepository.findByGroupIdIn(groupIds).maxOfOrNull { it.maxActive }
        if (groupMax != null) return EffectiveValue(groupMax, Source.GROUP_DEFAULT)
        val global = globalQuotaDefaultRepository
            .findById(GlobalQuotaDefault.SINGLETON_ID)
            .map { it.maxActive }
            .orElse(FALLBACK_GLOBAL_MAX_ACTIVE)
        return EffectiveValue(global, Source.GLOBAL_DEFAULT)
    }

    /** Null value means uncapped at this level (still bounded by the combined quota). */
    fun effectiveProviderMaxActive(userId: Long, provider: Provider): EffectiveValue<Int?> {
        userProviderQuotaOverrideRepository.findByIdUserIdAndIdProvider(userId, provider)?.let {
            return EffectiveValue(it.maxActive, Source.USER_OVERRIDE)
        }
        val groupIds = groupIdsFor(userId)
        val groupMax = groupProviderQuotaDefaultRepository
            .findByIdGroupIdInAndIdProvider(groupIds, provider)
            .maxOfOrNull { it.maxActive }
        if (groupMax != null) return EffectiveValue(groupMax, Source.GROUP_DEFAULT)
        val global = globalProviderQuotaDefaultRepository.findById(provider).map { it.maxActive }.orElse(null)
        return EffectiveValue(global, Source.GLOBAL_DEFAULT)
    }

    fun isProviderAllowed(userId: Long, provider: Provider): EffectiveValue<Boolean> {
        userProviderAccessRepository.findByIdUserIdAndIdProvider(userId, provider)?.let {
            return EffectiveValue(it.enabled, Source.USER_OVERRIDE)
        }
        val groupIds = groupIdsFor(userId)
        val groupAccess = groupProviderAccessRepository.findByIdGroupIdInAndIdProvider(groupIds, provider)
        if (groupAccess.isNotEmpty()) {
            return EffectiveValue(groupAccess.any { it.enabled }, Source.GROUP_DEFAULT)
        }
        // Fail closed: a provider with no seeded global row (e.g. added after GroupSeeder last ran) is denied by default.
        val global = globalProviderAccessRepository.findById(provider).map { it.enabled }.orElse(false)
        return EffectiveValue(global, Source.GLOBAL_DEFAULT)
    }

    private fun groupIdsFor(userId: Long): List<Long> = groupMemberRepository.findByIdUserId(userId).map { it.id.groupId }
}
