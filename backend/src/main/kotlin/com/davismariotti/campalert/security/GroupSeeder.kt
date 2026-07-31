package com.davismariotti.campalert.security

import com.davismariotti.campalert.model.GlobalProviderAccess
import com.davismariotti.campalert.model.GlobalProviderQuotaDefault
import com.davismariotti.campalert.model.GlobalQuotaDefault
import com.davismariotti.campalert.model.Group
import com.davismariotti.campalert.model.GroupAuthority
import com.davismariotti.campalert.model.GroupAuthorityId
import com.davismariotti.campalert.model.GroupMember
import com.davismariotti.campalert.model.GroupMemberId
import com.davismariotti.campalert.model.Permission
import com.davismariotti.campalert.model.PlatformSettings
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.repository.GlobalProviderAccessRepository
import com.davismariotti.campalert.repository.GlobalProviderQuotaDefaultRepository
import com.davismariotti.campalert.repository.GlobalQuotaDefaultRepository
import com.davismariotti.campalert.repository.GroupAuthorityRepository
import com.davismariotti.campalert.repository.GroupMemberRepository
import com.davismariotti.campalert.repository.GroupRepository
import com.davismariotti.campalert.repository.PlatformSettingsRepository
import com.davismariotti.campalert.repository.UserRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Idempotent startup seeding for the role/quota system.
 *
 * Groups are pure membership/permission-bundling containers, not authorities themselves (see
 * [Permission]). This seeds two baseline groups and their permission grants:
 * - "Standard": every ordinary capability in the app (profile, search requests, phone numbers,
 *   catalog browsing). Every user with zero group memberships is assigned here, so every user
 *   always resolves to at least one group without a separate one-time DB backfill script.
 * - "Admin": the admin-only permissions (dashboard, user/quota management, audit log). Seeded
 *   with its permissions but never auto-assigned to anyone — granting membership stays a
 *   deliberate manual step (design.md Appendix).
 *
 * Also ensures the global quota/provider-access default rows exist.
 */
@Component
class GroupSeeder(
    private val groupRepository: GroupRepository,
    private val groupAuthorityRepository: GroupAuthorityRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val globalQuotaDefaultRepository: GlobalQuotaDefaultRepository,
    private val globalProviderQuotaDefaultRepository: GlobalProviderQuotaDefaultRepository,
    private val globalProviderAccessRepository: GlobalProviderAccessRepository,
    private val platformSettingsRepository: PlatformSettingsRepository,
    private val userRepository: UserRepository,
) : ApplicationRunner {
    companion object {
        const val STANDARD_GROUP_NAME = "Standard"
        const val ADMIN_GROUP_NAME = "Admin"

        private val STANDARD_PERMISSIONS = setOf(
            Permission.VIEW_PROFILE,
            Permission.EDIT_PROFILE,
            Permission.VIEW_SEARCH_REQUESTS,
            Permission.MANAGE_SEARCH_REQUESTS,
            Permission.VIEW_PHONE_NUMBERS,
            Permission.MANAGE_PHONE_NUMBERS,
            Permission.VIEW_CAMPGROUNDS,
            Permission.VIEW_PERMITS,
        )

        private val ADMIN_PERMISSIONS = setOf(
            Permission.VIEW_ADMIN_DASHBOARD,
            Permission.MANAGE_USERS,
            Permission.MANAGE_GLOBAL_SETTINGS,
            Permission.VIEW_AUDIT_LOG,
            Permission.MANAGE_USER_SEARCH_REQUESTS,
            Permission.MANAGE_INVITES,
        )
    }

    @Transactional
    override fun run(args: ApplicationArguments) {
        seedGroup(STANDARD_GROUP_NAME, STANDARD_PERMISSIONS)
        seedGroup(ADMIN_GROUP_NAME, ADMIN_PERMISSIONS)
        seedGlobalDefaults()
        assignGrouplessUsersToStandard()
    }

    private fun seedGroup(groupName: String, permissions: Set<Permission>): Long {
        val group = groupRepository.findByGroupName(groupName)
            ?: groupRepository.save(Group(groupName = groupName))
        val groupId = group.id!!
        permissions.forEach { permission ->
            val authorityId = GroupAuthorityId(groupId, permission.name)
            if (!groupAuthorityRepository.existsById(authorityId)) {
                groupAuthorityRepository.save(GroupAuthority(authorityId))
            }
        }
        return groupId
    }

    private fun seedGlobalDefaults() {
        if (!globalQuotaDefaultRepository.existsById(GlobalQuotaDefault.SINGLETON_ID)) {
            globalQuotaDefaultRepository.save(GlobalQuotaDefault(maxActive = 5))
        }
        if (!globalProviderQuotaDefaultRepository.existsById(Provider.RESERVE_CALIFORNIA)) {
            globalProviderQuotaDefaultRepository.save(GlobalProviderQuotaDefault(Provider.RESERVE_CALIFORNIA, maxActive = 1))
        }
        val defaultAccess = mapOf(
            Provider.RECREATION_GOV to true,
            Provider.CAMPLIFE to true,
            Provider.RESERVE_CALIFORNIA to false,
        )
        defaultAccess.forEach { (provider, enabled) ->
            if (!globalProviderAccessRepository.existsById(provider)) {
                globalProviderAccessRepository.save(GlobalProviderAccess(provider, enabled))
            }
        }
        if (!platformSettingsRepository.existsById(PlatformSettings.SINGLETON_ID)) {
            platformSettingsRepository.save(PlatformSettings())
        }
    }

    private fun assignGrouplessUsersToStandard() {
        val standardGroupId = groupRepository.findByGroupName(STANDARD_GROUP_NAME)!!.id!!
        userRepository.findUsersWithNoGroup().forEach { user ->
            groupMemberRepository.save(GroupMember(GroupMemberId(user.id!!, standardGroupId)))
        }
    }
}
