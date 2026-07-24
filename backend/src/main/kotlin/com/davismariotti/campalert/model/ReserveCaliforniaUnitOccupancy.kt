package com.davismariotti.campalert.model

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant

/**
 * One row per ReserveCalifornia unit, shared across every search request targeting that
 * facility — occupancy is a property of the physical site, not of any individual search.
 * See `reserve-california-unit-occupancy-warmup` spec and design.md D12/D15/D20.
 */
@Entity
@Table(name = "reserve_california_unit_occupancy")
data class ReserveCaliforniaUnitOccupancy(
    @EmbeddedId
    val id: ReserveCaliforniaUnitOccupancyId,

    @Column(name = "unit_name")
    val unitName: String,

    @Column(name = "max_occupancy")
    val maxOccupancy: Int? = null,

    @Column(name = "min_occupancy")
    val minOccupancy: Int? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    val status: ReserveCaliforniaOccupancyStatus = ReserveCaliforniaOccupancyStatus.PENDING,

    @Column(name = "attempts")
    val attempts: Int = 0,

    /** Drives the D15 retry cooldown; null when status is FETCHED/EXCLUDED. */
    @Column(name = "next_attempt_at")
    val nextAttemptAt: Instant? = null,

    @Column(name = "fetched_at")
    val fetchedAt: Instant? = null,

    /** Drives the D20 staleness reset (90 days + per-unit jitter); set whenever status becomes FETCHED or EXCLUDED. */
    @Column(name = "next_reconcile_at")
    val nextReconcileAt: Instant? = null,
)
