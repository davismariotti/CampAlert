package com.davismariotti.campalert.model

import java.time.Instant

/**
 * Common surface the notification outbox needs from any searchable/alertable request type
 * (campground [SearchRequest], permit `PermitSearchRequest`), so the outbox and its processor
 * don't have to know which concrete request type they're handling.
 */
interface AlertableRequest {
    val id: Long?
    val userId: Long?
    var lastAvailabilityState: AvailabilityState?
    var lastNotifiedAt: Instant?
}
