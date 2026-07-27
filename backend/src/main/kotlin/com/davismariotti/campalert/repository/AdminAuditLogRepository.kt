package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.AdminAuditLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface AdminAuditLogRepository : JpaRepository<AdminAuditLog, Long> {
    fun findByTargetUserId(targetUserId: Long, pageable: Pageable): Page<AdminAuditLog>
}
