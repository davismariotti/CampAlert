package com.davismariotti.campalert.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "search_requests")
data class SearchRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "start_day")
    val startDay: LocalDate,

    @Column(name = "nights")
    val nights: Int,

    @Column(name = "group_size")
    val groupSize: Int,

    @Column(name = "campsite_id")
    val campsiteId: Int,

    @Column(name = "loops", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    val loops: List<String>? = null,

    @Column(name = "name")
    val name: String,

    @Column(name = "completed")
    val completed: Boolean,

    @Column(name = "user_id")
    val userId: Long? = null,

    @Column(name = "pause_reason")
    val pauseReason: String? = null,

    @Column(name = "campground_name")
    val campgroundName: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "last_availability_state")
    val lastAvailabilityState: AvailabilityState? = null,

    @Column(name = "user_paused", nullable = false)
    val userPaused: Boolean = false,

    @Column(name = "last_notified_at")
    val lastNotifiedAt: Instant? = null,

    @Column(name = "reminder_sent_at")
    val reminderSentAt: Instant? = null,

    @Column(name = "campground_timezone")
    val campgroundTimezone: String? = null,
)
