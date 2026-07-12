package com.davismariotti.campalert.model

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "poll_target_state")
data class PollTargetState(
    @EmbeddedId
    val id: PollTargetId,

    @Column(name = "phase_offset_ms", nullable = false)
    val phaseOffsetMs: Int,

    @Column(name = "next_due_at", nullable = false)
    val nextDueAt: Instant,

    @Column(name = "locked_until")
    val lockedUntil: Instant? = null,

    @Column(name = "last_started_at")
    val lastStartedAt: Instant? = null,

    @Column(name = "last_finished_at")
    val lastFinishedAt: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "last_status")
    val lastStatus: PollCheckStatus? = null,

    @Column(name = "last_error")
    val lastError: String? = null,
)
