package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.AdminAuditLogApiDelegate
import com.davismariotti.campalert.api.model.AdminAuditLogEntry
import com.davismariotti.campalert.api.model.AdminAuditLogListResponse
import com.davismariotti.campalert.repository.AdminAuditLogRepository
import com.davismariotti.campalert.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class AdminAuditLogDelegateImpl(
    private val adminAuditLogRepository: AdminAuditLogRepository,
    private val userRepository: UserRepository,
) : AdminAuditLogApiDelegate {
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
}
