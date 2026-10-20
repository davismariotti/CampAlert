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
 * Global Entry-only search request data — the optional time-of-day window a slot's start time must
 * fall in ([earliestTime] inclusive, [latestTime] exclusive; either bound null means open-ended).
 * Both are local to the enrollment center, matching the scheduler's own zone-less slot timestamps.
 * A row only exists when at least one bound was set.
 */
@Entity
@Table(name = "global_entry_search_request_details")
class GlobalEntrySearchRequestDetails {
    @Id
    @Column(name = "search_request_id")
    var searchRequestId: Long = 0

    @OneToOne
    @MapsId
    @JoinColumn(name = "search_request_id")
    var searchRequest: SearchRequest? = null

    @Column(name = "earliest_time")
    var earliestTime: LocalTime? = null

    @Column(name = "latest_time")
    var latestTime: LocalTime? = null
}
