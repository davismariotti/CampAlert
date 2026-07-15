package com.davismariotti.campalert.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "search_request_state")
class SearchRequestState {
    @Id
    @Column(name = "search_request_id")
    var searchRequestId: Long = 0

    @OneToOne
    @MapsId
    @JoinColumn(name = "search_request_id")
    var searchRequest: SearchRequest? = null

    @Column(name = "completed", nullable = false)
    var completed: Boolean = false

    @Column(name = "user_paused", nullable = false)
    var userPaused: Boolean = false

    @Column(name = "pause_reason")
    var pauseReason: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "last_availability_state")
    var lastAvailabilityState: AvailabilityState? = null

    @Column(name = "last_notified_at")
    var lastNotifiedAt: Instant? = null

    @Column(name = "reminder_sent_at")
    var reminderSentAt: Instant? = null

    @Column(name = "total_checks", nullable = false)
    var totalChecks: Int = 0

    @Column(name = "available_checks", nullable = false)
    var availableChecks: Int = 0

    @Column(name = "window_count", nullable = false)
    var windowCount: Int = 0

    @Column(name = "total_window_seconds", nullable = false)
    var totalWindowSeconds: Int = 0

    @Column(name = "became_available_at")
    var becameAvailableAt: Instant? = null

    /** The specific candidate stay currently matched within a flexible request's range; null when unavailable or exact-date. */
    @Column(name = "matched_start_day")
    var matchedStartDay: LocalDate? = null

    @Column(name = "matched_end_day")
    var matchedEndDay: LocalDate? = null
}
