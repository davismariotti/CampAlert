package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.AdminApiDelegate
import com.davismariotti.campalert.api.model.AdminAuditLogEntry
import com.davismariotti.campalert.api.model.AdminAuditLogListResponse
import com.davismariotti.campalert.api.model.AdminEffectiveProviderAccess
import com.davismariotti.campalert.api.model.AdminEffectiveProviderQuota
import com.davismariotti.campalert.api.model.AdminEffectiveQuota
import com.davismariotti.campalert.api.model.AdminEffectiveValueSource
import com.davismariotti.campalert.api.model.AdminGlobalProviderAccess
import com.davismariotti.campalert.api.model.AdminGlobalProviderQuota
import com.davismariotti.campalert.api.model.AdminGlobalSettingsResponse
import com.davismariotti.campalert.api.model.AdminGroupSummary
import com.davismariotti.campalert.api.model.AdminRequestCountByProvider
import com.davismariotti.campalert.api.model.AdminSetProviderAccessBody
import com.davismariotti.campalert.api.model.AdminSetQuotaBody
import com.davismariotti.campalert.api.model.AdminStatsResponse
import com.davismariotti.campalert.api.model.AdminUserDetailResponse
import com.davismariotti.campalert.api.model.AdminUserListResponse
import com.davismariotti.campalert.api.model.AdminUserSummary
import com.davismariotti.campalert.api.model.PermitSearchRequestResponse
import com.davismariotti.campalert.api.model.PhoneNumberResponse
import com.davismariotti.campalert.api.model.ProviderType
import com.davismariotti.campalert.api.model.SearchRequestResponse
import com.davismariotti.campalert.api.model.UpdatePermitSearchRequestBody
import com.davismariotti.campalert.api.model.UpdateSearchRequestBody
import com.davismariotti.campalert.exception.NotFoundException
import com.davismariotti.campalert.model.AdminAuditAction
import com.davismariotti.campalert.model.GlobalProviderAccess
import com.davismariotti.campalert.model.GlobalProviderQuotaDefault
import com.davismariotti.campalert.model.GlobalQuotaDefault
import com.davismariotti.campalert.model.GroupMember
import com.davismariotti.campalert.model.GroupMemberId
import com.davismariotti.campalert.model.PhoneNumber
import com.davismariotti.campalert.model.UserProviderAccess
import com.davismariotti.campalert.model.UserProviderAccessId
import com.davismariotti.campalert.model.UserProviderQuotaOverride
import com.davismariotti.campalert.model.UserProviderQuotaOverrideId
import com.davismariotti.campalert.model.UserQuotaOverride
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.repository.AdminAuditLogRepository
import com.davismariotti.campalert.repository.GlobalProviderAccessRepository
import com.davismariotti.campalert.repository.GlobalProviderQuotaDefaultRepository
import com.davismariotti.campalert.repository.GlobalQuotaDefaultRepository
import com.davismariotti.campalert.repository.GroupMemberRepository
import com.davismariotti.campalert.repository.GroupRepository
import com.davismariotti.campalert.repository.PermitSearchRequestRepository
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
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
import java.time.temporal.ChronoUnit

@Service
class AdminDelegateImpl(
    private val userRepository: UserRepository,
    private val phoneNumberRepository: PhoneNumberRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val userQuotaOverrideRepository: UserQuotaOverrideRepository,
    private val userProviderQuotaOverrideRepository: UserProviderQuotaOverrideRepository,
    private val userProviderAccessRepository: UserProviderAccessRepository,
    private val globalQuotaDefaultRepository: GlobalQuotaDefaultRepository,
    private val globalProviderQuotaDefaultRepository: GlobalProviderQuotaDefaultRepository,
    private val globalProviderAccessRepository: GlobalProviderAccessRepository,
    private val searchRequestRepository: SearchRequestRepository,
    private val permitSearchRequestRepository: PermitSearchRequestRepository,
    private val adminAuditLogRepository: AdminAuditLogRepository,
    private val userResourceLimitsService: UserResourceLimitsService,
    private val resourceReconciliationService: ResourceReconciliationService,
    private val adminAuditService: AdminAuditService,
    private val searchRequestsDelegateImpl: SearchRequestsDelegateImpl,
    private val permitSearchRequestsDelegateImpl: PermitSearchRequestsDelegateImpl,
) : AdminApiDelegate {
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

    // --- Stats ---

    @PreAuthorize("hasAuthority('VIEW_ADMIN_DASHBOARD')")
    override fun adminGetStats(): ResponseEntity<AdminStatsResponse> {
        val activeThreshold = Instant.now().minus(30, ChronoUnit.DAYS)
        val requestCounts = Provider.entries.map { provider ->
            AdminRequestCountByProvider(
                provider.toApiType(),
                searchRequestRepository.countByProvider(provider),
                permitSearchRequestRepository.countByProvider(provider),
            )
        }
        return ResponseEntity.ok(
            AdminStatsResponse(
                totalUsers = userRepository.count(),
                activeUsersLast30Days = userRepository.countByLastLoginAtAfter(activeThreshold),
                requestCounts = requestCounts,
            ),
        )
    }

    // --- User search / detail ---

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

    // --- Groups ---

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

    // --- Per-user quota / provider-access overrides ---

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

    // --- Global settings ---
    // Deliberately NOT eagerly reconciled (D4) — a global/group-level default change affects
    // potentially many users with no single per-user trigger; the poll-cycle guard is what catches
    // it, on that user's next poll cycle, not this write.

    @PreAuthorize("hasAuthority('MANAGE_GLOBAL_SETTINGS')")
    override fun adminGetGlobalSettings(): ResponseEntity<AdminGlobalSettingsResponse> {
        val combined = globalQuotaDefaultRepository.findById(GlobalQuotaDefault.SINGLETON_ID).map { it.maxActive }.orElse(5)
        val providerQuotas = Provider.entries.map { provider ->
            AdminGlobalProviderQuota(provider.toApiType(), globalProviderQuotaDefaultRepository.findById(provider).map { it.maxActive }.orElse(null))
        }
        val providerAccess = Provider.entries.map { provider ->
            AdminGlobalProviderAccess(provider.toApiType(), globalProviderAccessRepository.findById(provider).map { it.enabled }.orElse(false))
        }
        return ResponseEntity.ok(AdminGlobalSettingsResponse(combined, providerQuotas, providerAccess))
    }

    @Transactional
    @PreAuthorize("hasAuthority('MANAGE_GLOBAL_SETTINGS')")
    override fun adminSetGlobalQuota(adminSetQuotaBody: AdminSetQuotaBody): ResponseEntity<Unit> {
        globalQuotaDefaultRepository.save(GlobalQuotaDefault(GlobalQuotaDefault.SINGLETON_ID, adminSetQuotaBody.maxActive))
        adminAuditService.record(currentUserId(), null, AdminAuditAction.GLOBAL_QUOTA_DEFAULT_CHANGED, "combined=${adminSetQuotaBody.maxActive}")
        return ResponseEntity.noContent().build()
    }

    @Transactional
    @PreAuthorize("hasAuthority('MANAGE_GLOBAL_SETTINGS')")
    override fun adminSetGlobalProviderQuota(provider: ProviderType, adminSetQuotaBody: AdminSetQuotaBody): ResponseEntity<Unit> {
        val domainProvider = provider.toModel()
        globalProviderQuotaDefaultRepository.save(GlobalProviderQuotaDefault(domainProvider, adminSetQuotaBody.maxActive))
        adminAuditService.record(currentUserId(), null, AdminAuditAction.GLOBAL_QUOTA_DEFAULT_CHANGED, "$domainProvider=${adminSetQuotaBody.maxActive}")
        return ResponseEntity.noContent().build()
    }

    @Transactional
    @PreAuthorize("hasAuthority('MANAGE_GLOBAL_SETTINGS')")
    override fun adminClearGlobalProviderQuota(provider: ProviderType): ResponseEntity<Unit> {
        val domainProvider = provider.toModel()
        globalProviderQuotaDefaultRepository.deleteById(domainProvider)
        adminAuditService.record(currentUserId(), null, AdminAuditAction.GLOBAL_QUOTA_DEFAULT_CHANGED, "$domainProvider=uncapped")
        return ResponseEntity.noContent().build()
    }

    @Transactional
    @PreAuthorize("hasAuthority('MANAGE_GLOBAL_SETTINGS')")
    override fun adminSetGlobalProviderAccess(provider: ProviderType, adminSetProviderAccessBody: AdminSetProviderAccessBody): ResponseEntity<Unit> {
        val domainProvider = provider.toModel()
        globalProviderAccessRepository.save(GlobalProviderAccess(domainProvider, adminSetProviderAccessBody.enabled))
        adminAuditService.record(
            currentUserId(),
            null,
            AdminAuditAction.GLOBAL_PROVIDER_ACCESS_CHANGED,
            "$domainProvider=${adminSetProviderAccessBody.enabled}",
        )
        return ResponseEntity.noContent().build()
    }

    // --- Audit log ---

    @PreAuthorize("hasAuthority('VIEW_AUDIT_LOG')")
    override fun adminListAuditLog(targetUserId: Long?, page: Int, pageSize: Int): ResponseEntity<AdminAuditLogListResponse> {
        val pageable = PageRequest.of(page, pageSize)
        val result = if (targetUserId != null) {
            adminAuditLogRepository.findByTargetUserId(targetUserId, pageable)
        } else {
            adminAuditLogRepository.findAll(pageable)
        }
        val userIds = result.content.flatMap { listOfNotNull(it.actorUserId, it.targetUserId) }.toSet()
        val emailsById = userRepository.findAllById(userIds).associate { it.id to it.email }
        val items = result.content.map { entry ->
            AdminAuditLogEntry(
                id = entry.id!!,
                actorUserId = entry.actorUserId,
                action = entry.action,
                createdAt = OffsetDateTime.ofInstant(entry.createdAt, ZoneOffset.UTC),
                actorEmail = emailsById[entry.actorUserId],
                targetUserId = entry.targetUserId,
                targetEmail = entry.targetUserId?.let { emailsById[it] },
                detail = entry.detail,
            )
        }
        return ResponseEntity.ok(AdminAuditLogListResponse(items, result.totalElements, page, pageSize))
    }

    // --- Admin editing of a user's search requests (reuses the exact user-facing code path) ---

    @PreAuthorize("hasAuthority('MANAGE_USER_SEARCH_REQUESTS')")
    override fun adminListUserSearchRequests(id: Long, completed: Boolean?, deleted: Boolean): ResponseEntity<List<SearchRequestResponse>> {
        requireUser(id)
        return searchRequestsDelegateImpl.listSearchRequestsAs(id, completed, deleted)
    }

    @PreAuthorize("hasAuthority('MANAGE_USER_SEARCH_REQUESTS')")
    override fun adminUpdateUserSearchRequest(id: Long, requestId: Long, updateSearchRequestBody: UpdateSearchRequestBody): ResponseEntity<SearchRequestResponse> {
        requireUser(id)
        val response = searchRequestsDelegateImpl.updateSearchRequestAs(id, requestId, updateSearchRequestBody)
        adminAuditService.record(currentUserId(), id, AdminAuditAction.SEARCH_REQUEST_EDITED, "requestId=$requestId")
        return response
    }

    @Transactional
    @PreAuthorize("hasAuthority('MANAGE_USER_SEARCH_REQUESTS')")
    override fun adminDeleteUserSearchRequest(id: Long, requestId: Long): ResponseEntity<Unit> {
        requireUser(id)
        val response = searchRequestsDelegateImpl.deleteSearchRequestAs(id, requestId)
        adminAuditService.record(currentUserId(), id, AdminAuditAction.SEARCH_REQUEST_DELETED, "requestId=$requestId")
        return response
    }

    @PreAuthorize("hasAuthority('MANAGE_USER_SEARCH_REQUESTS')")
    override fun adminListUserPermitSearchRequests(id: Long, completed: Boolean?, deleted: Boolean): ResponseEntity<List<PermitSearchRequestResponse>> {
        requireUser(id)
        return permitSearchRequestsDelegateImpl.listPermitSearchRequestsAs(id, completed, deleted)
    }

    @PreAuthorize("hasAuthority('MANAGE_USER_SEARCH_REQUESTS')")
    override fun adminUpdateUserPermitSearchRequest(
        id: Long,
        requestId: Long,
        updatePermitSearchRequestBody: UpdatePermitSearchRequestBody,
    ): ResponseEntity<PermitSearchRequestResponse> {
        requireUser(id)
        val response = permitSearchRequestsDelegateImpl.updatePermitSearchRequestAs(id, requestId, updatePermitSearchRequestBody)
        adminAuditService.record(currentUserId(), id, AdminAuditAction.SEARCH_REQUEST_EDITED, "permitRequestId=$requestId")
        return response
    }

    @Transactional
    @PreAuthorize("hasAuthority('MANAGE_USER_SEARCH_REQUESTS')")
    override fun adminDeleteUserPermitSearchRequest(id: Long, requestId: Long): ResponseEntity<Unit> {
        requireUser(id)
        val response = permitSearchRequestsDelegateImpl.deletePermitSearchRequestAs(id, requestId)
        adminAuditService.record(currentUserId(), id, AdminAuditAction.SEARCH_REQUEST_DELETED, "permitRequestId=$requestId")
        return response
    }
}
