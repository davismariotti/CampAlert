package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.AdminGlobalSettingsApiDelegate
import com.davismariotti.campalert.api.model.AdminGlobalProviderAccess
import com.davismariotti.campalert.api.model.AdminGlobalProviderQuota
import com.davismariotti.campalert.api.model.AdminGlobalSettingsResponse
import com.davismariotti.campalert.api.model.AdminSetInviteOnlyBody
import com.davismariotti.campalert.api.model.AdminSetProviderAccessBody
import com.davismariotti.campalert.api.model.AdminSetQuotaBody
import com.davismariotti.campalert.api.model.ProviderType
import com.davismariotti.campalert.model.AdminAuditAction
import com.davismariotti.campalert.model.GlobalProviderAccess
import com.davismariotti.campalert.model.GlobalProviderQuotaDefault
import com.davismariotti.campalert.model.GlobalQuotaDefault
import com.davismariotti.campalert.model.PlatformSettings
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.repository.GlobalProviderAccessRepository
import com.davismariotti.campalert.repository.GlobalProviderQuotaDefaultRepository
import com.davismariotti.campalert.repository.GlobalQuotaDefaultRepository
import com.davismariotti.campalert.repository.PlatformSettingsRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.AdminAuditService
import com.davismariotti.campalert.util.currentUserId
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// Global/group-level default changes are deliberately NOT eagerly reconciled (D4) — they affect
// potentially many users with no single per-user trigger; the poll-cycle guard is what catches it,
// on each user's next poll cycle, not this write.
@Service
class AdminGlobalSettingsDelegateImpl(
    private val userRepository: UserRepository,
    private val globalQuotaDefaultRepository: GlobalQuotaDefaultRepository,
    private val globalProviderQuotaDefaultRepository: GlobalProviderQuotaDefaultRepository,
    private val globalProviderAccessRepository: GlobalProviderAccessRepository,
    private val platformSettingsRepository: PlatformSettingsRepository,
    private val adminAuditService: AdminAuditService,
) : AdminGlobalSettingsApiDelegate {
    private fun currentUserId(): Long = currentUserId(userRepository)

    @PreAuthorize("hasAuthority('MANAGE_GLOBAL_SETTINGS')")
    override fun adminGetGlobalSettings(): ResponseEntity<AdminGlobalSettingsResponse> {
        val combined = globalQuotaDefaultRepository.findById(GlobalQuotaDefault.SINGLETON_ID).map { it.maxActive }.orElse(5)
        val providerQuotas = Provider.entries.map { provider ->
            AdminGlobalProviderQuota(provider.toApiType(), globalProviderQuotaDefaultRepository.findById(provider).map { it.maxActive }.orElse(null))
        }
        val providerAccess = Provider.entries.map { provider ->
            AdminGlobalProviderAccess(provider.toApiType(), globalProviderAccessRepository.findById(provider).map { it.enabled }.orElse(false))
        }
        val inviteOnlyEnabled = platformSettingsRepository
            .findById(PlatformSettings.SINGLETON_ID)
            .map { it.inviteOnlyEnabled }
            .orElse(false)
        return ResponseEntity.ok(AdminGlobalSettingsResponse(combined, providerQuotas, providerAccess, inviteOnlyEnabled))
    }

    @Transactional
    @PreAuthorize("hasAuthority('MANAGE_GLOBAL_SETTINGS')")
    override fun adminSetInviteOnly(adminSetInviteOnlyBody: AdminSetInviteOnlyBody): ResponseEntity<Unit> {
        platformSettingsRepository.save(PlatformSettings(inviteOnlyEnabled = adminSetInviteOnlyBody.enabled))
        adminAuditService.record(currentUserId(), null, AdminAuditAction.INVITE_ONLY_MODE_CHANGED, "enabled=${adminSetInviteOnlyBody.enabled}")
        return ResponseEntity.noContent().build()
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
}
