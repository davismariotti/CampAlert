package com.davismariotti.campalert.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.LocalTime

/**
 * Global Entry-only observed state: the earliest matching slot's local start time and how many
 * slots matched on the last check that found availability. Persisted (rather than recomputed) because
 * the alert is built after the outbox `send_after` delay from the request and its state alone. Both
 * are null while the request is unavailable.
 */
@Entity
@Table(name = "global_entry_search_request_state")
class GlobalEntrySearchRequestState {
    @Id
    @Column(name = "search_request_id")
    var searchRequestId: Long = 0

    @OneToOne
    @MapsId
    @JoinColumn(name = "search_request_id")
    var searchRequest: SearchRequest? = null

    @Column(name = "matched_start_time")
    var matchedStartTime: LocalTime? = null

    @Column(name = "matched_slot_count")
    var matchedSlotCount: Int? = null
}
