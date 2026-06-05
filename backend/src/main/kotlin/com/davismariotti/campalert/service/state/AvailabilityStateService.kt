package com.davismariotti.campalert.service.state

import com.davismariotti.campalert.model.NotificationOutbox
import com.davismariotti.campalert.model.SearchRequestCheck
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.repository.NotificationOutboxRepository
import com.davismariotti.campalert.repository.SearchRequestCheckRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.service.availability.AvailabilityResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@Service
class AvailabilityStateService(
    private val searchRequestRepository: SearchRequestRepository,
    private val searchRequestCheckRepository: SearchRequestCheckRepository,
    private val notificationOutboxRepository: NotificationOutboxRepository,
) {
    @Transactional
    fun processUserResults(results: List<AvailabilityResult>, user: User) {
        val now = Instant.now()
        results.forEach { result -> processResult(result, user, now) }
    }

    private fun processResult(result: AvailabilityResult, user: User, now: Instant) {
        val request = result.searchRequest
        val hasAvailable = result.hasAvailableSites
        val currentState = request.lastAvailabilityState
        val newState = if (hasAvailable) "AVAILABLE" else "UNAVAILABLE"

        searchRequestCheckRepository.save(
            SearchRequestCheck(
                searchRequestId = request.id!!,
                checkedAt = now,
                available = hasAvailable,
                availableSiteCount = result.campground.campsites.size,
            ),
        )

        val outboxType: String? = when {
            // null → AVAILABLE or UNAVAILABLE → AVAILABLE: alert
            (currentState == null || currentState == "UNAVAILABLE") && hasAvailable -> "AVAILABLE"

            // AVAILABLE → UNAVAILABLE: gone alert; clears pause/reminder state
            currentState == "AVAILABLE" && !hasAvailable -> "UNAVAILABLE"

            // AVAILABLE → AVAILABLE: reminder if eligible
            currentState == "AVAILABLE" && hasAvailable -> {
                when {
                    request.userPaused -> null
                    request.reminderSentAt != null -> null
                    request.lastNotifiedAt == null -> null
                    Duration.between(request.lastNotifiedAt, now) > Duration.ofMinutes(30) -> "REMINDER"
                    else -> null
                }
            }

            // null → UNAVAILABLE or UNAVAILABLE → UNAVAILABLE: no notification
            else -> null
        }

        var updated = request.copy(lastAvailabilityState = newState)
        if (currentState == "AVAILABLE" && !hasAvailable) {
            updated = updated.copy(userPaused = false, reminderSentAt = null)
        }
        if (outboxType == "REMINDER") {
            updated = updated.copy(reminderSentAt = now)
        }
        searchRequestRepository.save(updated)

        if (outboxType != null) {
            val sendAfter = computeSendAfter(now, user.timezone)
            notificationOutboxRepository.save(
                NotificationOutbox(
                    userId = user.id!!,
                    requestId = request.id,
                    type = outboxType,
                    sendAfter = sendAfter,
                ),
            )
        }
    }

    /** Returns `now()` outside quiet hours (1am–6am local) or the next 6am if inside. */
    internal fun computeSendAfter(now: Instant, timezone: String): Instant {
        val zone = try {
            ZoneId.of(timezone)
        } catch (_: Exception) {
            ZoneId.of("UTC")
        }
        val local = now.atZone(zone)
        val hour = local.hour
        return if (hour in 1 until 6) {
            local.toLocalDate().atTime(6, 0).atZone(zone).toInstant()
        } else {
            now
        }
    }
}
