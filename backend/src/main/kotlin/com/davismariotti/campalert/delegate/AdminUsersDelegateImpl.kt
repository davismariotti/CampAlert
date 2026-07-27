package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.AdminUsersApiDelegate
import com.davismariotti.campalert.api.model.AdminEffectiveProviderAccess
import com.davismariotti.campalert.api.model.AdminEffectiveProviderQuota
import com.davismariotti.campalert.api.model.AdminEffectiveQuota
import com.davismariotti.campalert.api.model.AdminEffectiveValueSource
import com.davismariotti.campalert.api.model.AdminGroupSummary
import com.davismariotti.campalert.api.model.AdminSetProviderAccessBody
import com.davismariotti.campalert.api.model.AdminSetQuotaBody
import com.davismariotti.campalert.api.model.AdminUserDetailResponse
import com.davismariotti.campalert.api.model.AdminUserListResponse
import com.davismariotti.campalert.api.model.AdminUserSummary
import com.davismariotti.campalert.api.model.PhoneNumberResponse
import com.davismariotti.campalert.api.model.ProviderType
import com.davismariotti.campalert.exception.NotFoundException
import com.davismariotti.campalert.model.AdminAuditAction
import com.davismariotti.campalert.model.GroupMember
import com.davismariotti.campalert.model.GroupMemberId
import com.davismariotti.campalert.model.PhoneNumber
import com.davismariotti.campalert.model.UserProviderAccess
import com.davismariotti.campalert.model.UserProviderAccessId
import com.davismariotti.campalert.model.UserProviderQuotaOverride
import com.davismariotti.campalert.model.UserProviderQuotaOverrideId
import com.davismariotti.campalert.model.UserQuotaOverride
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.repository.GroupMemberRepository
import com.davismariotti.campalert.repository.GroupRepository
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.UserProviderAccessRepository
import com.davismariotti.campalert.repository.UserProviderQuotaOverrideRepository
import com.davismariotti.campalert.repository.UserQuotaOverrideRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.AdminAuditService
import com.davismariotti.campalert.service.ResourceReconciliationService
import com.davismariotti.campalert.service.UserResourceLimitsService
import com.davismariotti.campalert.util.currentUserId
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class AdminUsersDelegateImpl(
    private val userRepository: UserRepository,
    private val phoneNumberRepository: PhoneNumberRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val userQuotaOverrideRepository: UserQuotaOverrideRepository,
    private val userProviderQuotaOverrideRepository: UserProviderQuotaOverrideRepository,
    private val userProviderAccessRepository: UserProviderAccessRepository,
    private val userResourceLimitsService: UserResourceLimitsService,
    private val resourceReconciliationService: ResourceReconciliationService,
    private val adminAuditService: AdminAuditService,
) : AdminUsersApiDelegate {
    private fun currentUserId(): Long = currentUserId(userRepository)

    private fun requireUser(id: Long) = userRepository.findById(id).orElse(null) ?: throw NotFoundException("User not found")

    private fun Instant?.toApi(): OffsetDateTime? = this?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) }

    private fun PhoneNumber.toResponse() =
        PhoneNumberResponse(
            id = this.id!!,
            phone = this.phone,
            status = PhoneNumberResponse.Status.valueOf(this.status.name),
            smsConsentAt = OffsetDateTime.ofInstant(this.smsConsentAt, ZoneOffset.UTC),
            createdAt = OffsetDateTime.ofInstant(this.createdAt, ZoneOffset.UTC),
            firstMessageSent = this.firstMessageSent,
            verifiedAt = this.verifiedAt.toApi(),
            requiresCarrierOptIn = false,
        )

    private fun UserResourceLimitsService.Source.toApi(): AdminEffectiveValueSource =
        when (this) {
            UserResourceLimitsService.Source.USER_OVERRIDE -> AdminEffectiveValueSource.USER_OVERRIDE
            UserResourceLimitsService.Source.GROUP_DEFAULT -> AdminEffectiveValueSource.GROUP_DEFAULT
            UserResourceLimitsService.Source.GLOBAL_DEFAULT -> AdminEffectiveValueSource.GLOBAL_DEFAULT
        }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    override fun adminListUsers(query: String?, page: Int, pageSize: Int): ResponseEntity<AdminUserListResponse> {
        val normalizedQuery = query?.takeIf { it.isNotBlank() }
        val result = userRepository.searchUsers(normalizedQuery, PageRequest.of(page, pageSize))
        val items = result.content.map { AdminUserSummary(it.id!!, it.email, it.lastLoginAt.toApi()) }
        return ResponseEntity.ok(AdminUserListResponse(items, result.totalElements, page, pageSize))
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    override fun adminGetUser(id: Long): ResponseEntity<AdminUserDetailResponse> {
        val user = requireUser(id)
        val phoneNumbers = phoneNumberRepository.findByUserId(id).map { it.toResponse() }
        val groupIds = groupMemberRepository.findByIdUserId(id).map { it.id.groupId }
        val groups = groupRepository.findAllById(groupIds).map { AdminGroupSummary(it.id!!, it.groupName) }

        val combined = userResourceLimitsService.effectiveCombinedMaxActive(id)
        val providerQuotas = Provider.entries.map { provider ->
            val effective = userResourceLimitsService.effectiveProviderMaxActive(id, provider)
            AdminEffectiveProviderQuota(provider.toApiType(), effective.source.toApi(), effective.value)
        }
        val providerAccess = Provider.entries.map { provider ->
            val effective = userResourceLimitsService.isProviderAllowed(id, provider)
            AdminEffectiveProviderAccess(provider.toApiType(), effective.value, effective.source.toApi())
        }

        return ResponseEntity.ok(
            AdminUserDetailResponse(
                id = user.id!!,
                email = user.email,
                timezone = user.timezone,
                phoneNumbers = phoneNumbers,
                groups = groups,
                effectiveCombinedQuota = AdminEffectiveQuota(combined.value, combined.source.toApi()),
                effectiveProviderQuotas = providerQuotas,
                effectiveProviderAccess = providerAccess,
                emailVerifiedAt = user.emailVerifiedAt.toApi(),
                lastLoginAt = user.lastLoginAt.toApi(),
            ),
        )
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    override fun adminListGroups(): ResponseEntity<List<AdminGroupSummary>> = ResponseEntity.ok(groupRepository.findAll().map { AdminGroupSummary(it.id!!, it.groupName) })

    @Transactional
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    override fun adminAddUserToGroup(id: Long, groupId: Long): ResponseEntity<Unit> {
        val user = requireUser(id)
        val group = groupRepository.findById(groupId).orElse(null) ?: throw NotFoundException("Group not found")
        val membershipId = GroupMemberId(user.id!!, group.id!!)
        if (!groupMemberRepository.existsById(membershipId)) {
            groupMemberRepository.save(GroupMember(membershipId))
        }
        resourceReconciliationService.reconcileUser(id)
        adminAuditService.record(currentUserId(), id, AdminAuditAction.GROUP_MEMBERSHIP_ADDED, group.groupName)
        return ResponseEntity.noContent().build()
    }

    @Transactional
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    override fun adminRemoveUserFromGroup(id: Long, groupId: Long): ResponseEntity<Unit> {
        val user = requireUser(id)
        val group = groupRepository.findById(groupId).orElse(null) ?: throw NotFoundException("Group not found")
        groupMemberRepository.deleteById(GroupMemberId(user.id!!, group.id!!))
        resourceReconciliationService.reconcileUser(id)
        adminAuditService.record(currentUserId(), id, AdminAuditAction.GROUP_MEMBERSHIP_REMOVED, group.groupName)
        return ResponseEntity.noContent().build()
    }

    @Transactional
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    override fun adminSetUserQuota(id: Long, adminSetQuotaBody: AdminSetQuotaBody): ResponseEntity<Unit> {
        requireUser(id)
        userQuotaOverrideRepository.save(UserQuotaOverride(id, adminSetQuotaBody.maxActive))
        resourceReconciliationService.reconcileUser(id)
        adminAuditService.record(currentUserId(), id, AdminAuditAction.USER_QUOTA_OVERRIDE_SET, "combined=${adminSetQuotaBody.maxActive}")
        return ResponseEntity.noContent().build()
    }

    @Transactional
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    override fun adminClearUserQuota(id: Long): ResponseEntity<Unit> {
        requireUser(id)
        userQuotaOverrideRepository.deleteById(id)
        resourceReconciliationService.reconcileUser(id)
        adminAuditService.record(currentUserId(), id, AdminAuditAction.USER_QUOTA_OVERRIDE_CLEARED, "combined")
        return ResponseEntity.noContent().build()
    }

    @Transactional
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    override fun adminSetUserProviderQuota(id: Long, provider: ProviderType, adminSetQuotaBody: AdminSetQuotaBody): ResponseEntity<Unit> {
        requireUser(id)
        val domainProvider = provider.toModel()
        userProviderQuotaOverrideRepository.save(
            UserProviderQuotaOverride(UserProviderQuotaOverrideId(id, domainProvider), adminSetQuotaBody.maxActive),
        )
        resourceReconciliationService.reconcileUser(id)
        adminAuditService.record(currentUserId(), id, AdminAuditAction.USER_QUOTA_OVERRIDE_SET, "$domainProvider=${adminSetQuotaBody.maxActive}")
        return ResponseEntity.noContent().build()
    }

    @Transactional
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    override fun adminClearUserProviderQuota(id: Long, provider: ProviderType): ResponseEntity<Unit> {
        requireUser(id)
        val domainProvider = provider.toModel()
        userProviderQuotaOverrideRepository.deleteByIdUserIdAndIdProvider(id, domainProvider)
        resourceReconciliationService.reconcileUser(id)
        adminAuditService.record(currentUserId(), id, AdminAuditAction.USER_QUOTA_OVERRIDE_CLEARED, domainProvider.toString())
        return ResponseEntity.noContent().build()
    }

    @Transactional
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    override fun adminSetUserProviderAccess(id: Long, provider: ProviderType, adminSetProviderAccessBody: AdminSetProviderAccessBody): ResponseEntity<Unit> {
        requireUser(id)
        val domainProvider = provider.toModel()
        userProviderAccessRepository.save(
            UserProviderAccess(UserProviderAccessId(id, domainProvider), adminSetProviderAccessBody.enabled),
        )
        resourceReconciliationService.reconcileUser(id)
        adminAuditService.record(
            currentUserId(),
            id,
            AdminAuditAction.USER_PROVIDER_ACCESS_SET,
            "$domainProvider=${adminSetProviderAccessBody.enabled}",
        )
        return ResponseEntity.noContent().build()
    }

    @Transactional
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    override fun adminClearUserProviderAccess(id: Long, provider: ProviderType): ResponseEntity<Unit> {
        requireUser(id)
        val domainProvider = provider.toModel()
        userProviderAccessRepository.deleteByIdUserIdAndIdProvider(id, domainProvider)
        resourceReconciliationService.reconcileUser(id)
        adminAuditService.record(currentUserId(), id, AdminAuditAction.USER_PROVIDER_ACCESS_CLEARED, domainProvider.toString())
        return ResponseEntity.noContent().build()
    }
}
