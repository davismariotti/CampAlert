package com.davismariotti.campalert.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** One row per successful signup against an invite — drives redemption progress and per-user audit. */
@Entity
@Table(name = "invite_redemptions")
data class InviteRedemption(
    @Id
    val id: UUID,

    @Column(name = "invite_id", nullable = false)
    val inviteId: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "redeemed_at", nullable = false)
    val redeemedAt: Instant,
)
