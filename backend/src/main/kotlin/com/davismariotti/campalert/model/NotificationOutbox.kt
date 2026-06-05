package com.davismariotti.campalert.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "notification_outbox")
data class NotificationOutbox(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "request_id", nullable = false)
    val requestId: Int,

    @Column(name = "type", nullable = false)
    val type: String,

    @Column(name = "send_after", nullable = false)
    val sendAfter: Instant,

    @Column(name = "sent_at")
    val sentAt: Instant? = null,

    @Column(name = "missed_at")
    val missedAt: Instant? = null,

    @Column(name = "claimed_at")
    val claimedAt: Instant? = null,

    @Column(name = "attempt_count", nullable = false)
    val attemptCount: Int = 0,
)
