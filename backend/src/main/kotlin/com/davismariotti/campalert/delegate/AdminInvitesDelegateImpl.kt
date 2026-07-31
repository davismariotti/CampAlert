package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.AdminInvitesApiDelegate
import com.davismariotti.campalert.api.model.AdminCreateEmailInvitesBody
import com.davismariotti.campalert.api.model.AdminCreateEmailInvitesResponse
import com.davismariotti.campalert.api.model.AdminCreateInviteLinkBody
import com.davismariotti.campalert.api.model.AdminEmailInviteResult
import com.davismariotti.campalert.api.model.AdminInviteLinkResponse
import com.davismariotti.campalert.api.model.AdminInviteListResponse
import com.davismariotti.campalert.api.model.AdminInviteStatus
import com.davismariotti.campalert.api.model.AdminInviteSummary
import com.davismariotti.campalert.api.model.AdminInviteType
import com.davismariotti.campalert.model.AdminAuditAction
import com.davismariotti.campalert.model.Invite
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.AdminAuditService
import com.davismariotti.campalert.service.invite.InviteService
import com.davismariotti.campalert.util.currentUserId
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class AdminInvitesDelegateImpl(
    private val inviteService: InviteService,
    private val userRepository: UserRepository,
    private val adminAuditService: AdminAuditService,
    @Value("\${campfinder.email.frontend-base-url}") private val frontendBaseUrl: String,
) : AdminInvitesApiDelegate {
    private fun currentUserId(): Long = currentUserId(userRepository)

    @Transactional
    @PreAuthorize("hasAuthority('MANAGE_INVITES')")
    override fun adminCreateEmailInvites(adminCreateEmailInvitesBody: AdminCreateEmailInvitesBody): ResponseEntity<AdminCreateEmailInvitesResponse> {
        val actingAdminId = currentUserId()
        val results = inviteService.createEmailInvites(
            adminCreateEmailInvitesBody.emails,
            adminCreateEmailInvitesBody.expiresInDays?.toLong(),
            actingAdminId,
        )
        adminAuditService.record(
            actingAdminId,
            null,
            AdminAuditAction.INVITE_CREATED,
            "emails=${results.filter { it.created }.joinToString(",") { it.email }}",
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(
            AdminCreateEmailInvitesResponse(results.map { AdminEmailInviteResult(it.email, it.created, it.reason) }),
        )
    }

    @Transactional
    @PreAuthorize("hasAuthority('MANAGE_INVITES')")
    override fun adminCreateInviteLink(adminCreateInviteLinkBody: AdminCreateInviteLinkBody): ResponseEntity<AdminInviteLinkResponse> {
        val actingAdminId = currentUserId()
        val (invite, token) = inviteService.createPublicLink(
            adminCreateInviteLinkBody.maxUses,
            adminCreateInviteLinkBody.expiresInDays?.toLong(),
            actingAdminId,
        )
        adminAuditService.record(actingAdminId, null, AdminAuditAction.INVITE_CREATED, "link maxUses=${adminCreateInviteLinkBody.maxUses}")
        val url = "$frontendBaseUrl/register?inviteId=${invite.id}&token=$token"
        return ResponseEntity.status(HttpStatus.CREATED).body(AdminInviteLinkResponse(invite.toSummary(), url))
    }

    @PreAuthorize("hasAuthority('MANAGE_INVITES')")
    override fun adminListInvites(includeInactive: Boolean, page: Int, pageSize: Int): ResponseEntity<AdminInviteListResponse> {
        val result = inviteService.listInvites(includeInactive, PageRequest.of(page, pageSize))
        return ResponseEntity.ok(
            AdminInviteListResponse(
                items = result.content.map { it.toSummary() },
                total = result.totalElements,
                page = page,
                pageSize = pageSize,
            ),
        )
    }

    @Transactional
    @PreAuthorize("hasAuthority('MANAGE_INVITES')")
    override fun adminDeactivateInvite(id: UUID): ResponseEntity<Unit> {
        inviteService.deactivateInvite(id)
        adminAuditService.record(currentUserId(), null, AdminAuditAction.INVITE_DEACTIVATED, "$id")
        return ResponseEntity.noContent().build()
    }

    private fun Invite.toSummary(): AdminInviteSummary {
        val creatorEmail = userRepository.findById(createdByUserId).map { it.email }.orElse("unknown")
        return AdminInviteSummary(
            id = id,
            type = if (email != null) AdminInviteType.EMAIL else AdminInviteType.LINK,
            status = AdminInviteStatus.valueOf(derivedStatus(Instant.now()).name),
            maxUses = maxUses,
            usedCount = usedCount,
            createdByEmail = creatorEmail,
            createdAt = OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC),
            expiresAt = OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC),
            email = email,
            deactivatedAt = deactivatedAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) },
        )
    }
}
