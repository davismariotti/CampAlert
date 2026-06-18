package com.davismariotti.campalert.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "search_request_checks")
data class SearchRequestCheck(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "search_request_id", nullable = false)
    val searchRequestId: Long,

    @Column(name = "checked_at", nullable = false)
    val checkedAt: Instant,

    @Column(name = "available", nullable = false)
    val available: Boolean,

    @Column(name = "available_site_count", nullable = false)
    val availableSiteCount: Int,
)
