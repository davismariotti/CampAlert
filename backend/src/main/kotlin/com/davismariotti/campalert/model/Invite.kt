package com.davismariotti.campalert.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Unified model for both targeted email invites (email non-null, maxUses = 1) and capacity-based
 * public links (email null, maxUses >= 1). Status is derived at query/read time from
 * deactivatedAt/expiresAt/usedCount, not stored — see design.md D2.
 */
@Entity
@Table(name = "invites")
data class Invite(
    @Id
    val id: UUID,

    @Column(name = "email")
    val email: String? = null,

    @Column(name = "token_hash", nullable = false)
    val tokenHash: String,

    @Column(name = "max_uses", nullable = false)
    val maxUses: Int = 1,

    @Column(name = "used_count", nullable = false)
    val usedCount: Int = 0,

    @Column(name = "created_by_user_id", nullable = false)
    val createdByUserId: Long,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "deactivated_at")
    val deactivatedAt: Instant? = null,
) {
    enum class DerivedStatus { ACTIVE, EXPIRED, DEACTIVATED, REDEEMED }

    fun derivedStatus(now: Instant): DerivedStatus =
        when {
            deactivatedAt != null -> DerivedStatus.DEACTIVATED
            usedCount >= maxUses -> DerivedStatus.REDEEMED
            expiresAt.isBefore(now) -> DerivedStatus.EXPIRED
            else -> DerivedStatus.ACTIVE
        }
}
