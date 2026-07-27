package com.davismariotti.campalert.service

import com.davismariotti.campalert.model.AdminAuditAction
import com.davismariotti.campalert.model.AdminAuditLog
import com.davismariotti.campalert.repository.AdminAuditLogRepository
import org.springframework.stereotype.Service

/** Records every admin write path (quota/provider-access/group-membership/global-settings changes, search-request edits) to `admin_audit_log`. No retention/pruning policy (design.md). */
@Service
class AdminAuditService(
    private val adminAuditLogRepository: AdminAuditLogRepository,
) {
    fun record(
        actorUserId: Long,
        targetUserId: Long?,
        action: AdminAuditAction,
        detail: String? = null
    ) {
        adminAuditLogRepository.save(
            AdminAuditLog(
                actorUserId = actorUserId,
                targetUserId = targetUserId,
                action = action.name,
                detail = detail,
            ),
        )
    }
}
