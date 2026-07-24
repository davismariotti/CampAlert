package com.davismariotti.campalert.provider.reservecalifornia

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "campfinder.reservecalifornia.occupancy")
data class ReserveCaliforniaOccupancyProperties(
    /** D15: failed attempts before a unit's occupancy is marked permanently excluded. */
    val maxAttempts: Int = 5,
    /** D15: minimum wait before retrying a FAILED row. */
    val retryCooldownMinutes: Long = 10,
    /** D20: base freshness window before a FETCHED/EXCLUDED row is eligible for reset. */
    val freshnessDays: Long = 90,
    /** D20: per-unit random jitter (0..this, inclusive) added on top of [freshnessDays] so a facility's whole roster doesn't expire simultaneously. */
    val freshnessJitterDays: Long = 14,
)
