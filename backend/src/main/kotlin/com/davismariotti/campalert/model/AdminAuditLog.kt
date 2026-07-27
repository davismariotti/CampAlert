package com.davismariotti.campalert.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class AdminAuditAction {
    GROUP_MEMBERSHIP_ADDED,
    GROUP_MEMBERSHIP_REMOVED,
    USER_QUOTA_OVERRIDE_SET,
    USER_QUOTA_OVERRIDE_CLEARED,
    USER_PROVIDER_ACCESS_SET,
    USER_PROVIDER_ACCESS_CLEARED,
    GLOBAL_QUOTA_DEFAULT_CHANGED,
    GLOBAL_PROVIDER_ACCESS_CHANGED,
    SEARCH_REQUEST_EDITED,
    SEARCH_REQUEST_DELETED,
}

@Entity
@Table(name = "admin_audit_log")
data class AdminAuditLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "actor_user_id", nullable = false)
    val actorUserId: Long,

    @Column(name = "target_user_id")
    val targetUserId: Long? = null,

    @Column(name = "action", nullable = false)
    val action: String,

    @Column(name = "detail")
    val detail: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
