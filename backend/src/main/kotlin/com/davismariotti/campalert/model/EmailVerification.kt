package com.davismariotti.campalert.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "email_verifications")
data class EmailVerification(
    @Id
    val id: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "code_hash", nullable = false)
    val codeHash: String,

    @Column(name = "attempts", nullable = false)
    val attempts: Short = 0,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "consumed_at")
    val consumedAt: Instant? = null,
)
