package com.davismariotti.campalert.service.state

import com.davismariotti.campalert.model.AvailabilityState
import com.davismariotti.campalert.model.NotificationOutbox
import com.davismariotti.campalert.model.OutboxType
import com.davismariotti.campalert.model.SearchRequestCheck
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.repository.NotificationOutboxRepository
import com.davismariotti.campalert.repository.SearchRequestCheckRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.service.availability.AvailabilityResult
import com.newrelic.api.agent.NewRelic
import org.slf4j.LoggerFactory
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
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun processUserResults(results: List<AvailabilityResult>, user: User) {
        val now = Instant.now()
        results.forEach { result -> processResult(result, user, now) }
    }

    private fun processResult(result: AvailabilityResult, user: User, now: Instant) {
        val request = result.searchRequest
        val hasAvailable = result.hasAvailableSites
        val currentState = request.lastAvailabilityState
        val newState = if (hasAvailable) AvailabilityState.AVAILABLE else AvailabilityState.UNAVAILABLE

        searchRequestCheckRepository.save(
            SearchRequestCheck(
                searchRequestId = request.id!!,
                checkedAt = now,
                available = hasAvailable,
                availableSiteCount = result.campground.campsites.size,
            ),
        )

        val outboxType: OutboxType? = when {
            // null → AVAILABLE or UNAVAILABLE → AVAILABLE: alert
            (currentState == null || currentState == AvailabilityState.UNAVAILABLE) && hasAvailable -> OutboxType.AVAILABLE

            // AVAILABLE → UNAVAILABLE: gone alert; clears pause/reminder state
            currentState == AvailabilityState.AVAILABLE && !hasAvailable -> OutboxType.UNAVAILABLE

            // AVAILABLE → AVAILABLE: reminder if eligible
            currentState == AvailabilityState.AVAILABLE && hasAvailable -> {
                when {
                    request.userPaused -> null
                    request.reminderSentAt != null -> null
                    request.lastNotifiedAt == null -> null
                    Duration.between(request.lastNotifiedAt, now) > Duration.ofMinutes(30) -> OutboxType.REMINDER
                    else -> null
                }
            }

            // null → UNAVAILABLE or UNAVAILABLE → UNAVAILABLE: no notification
            else -> null
        }

        var updated = request.copy(lastAvailabilityState = newState)
        if (currentState == AvailabilityState.AVAILABLE && !hasAvailable) {
            updated = updated.copy(userPaused = false, reminderSentAt = null)
        }
        if (outboxType == OutboxType.REMINDER) {
            updated = updated.copy(reminderSentAt = now)
        }
        searchRequestRepository.save(updated)

        if (outboxType != null) {
            log.info(
                "Availability transition requestId={} campsiteId={} from={} to={} availableSites={} outboxType={}",
                request.id,
                request.campsiteId,
                currentState,
                newState,
                result.campground.campsites.size,
                outboxType,
            )
            NewRelic.getAgent().insights.recordCustomEvent(
                "AvailabilityStateChange",
                mapOf(
                    "userId" to user.id!!,
                    "requestId" to request.id!!,
                    "campsiteId" to request.campsiteId,
                    "campgroundName" to (request.campgroundName ?: ""),
                    "from" to (currentState?.name ?: "null"),
                    "to" to newState.name,
                    "availableSiteCount" to result.campground.campsites.size,
                ),
            )

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
            ZoneId.of("America/Los_Angeles")
        }
        val local = now.atZone(zone)
        val hour = local.hour
        return if (hour in 1 until 6) {
            local
                .toLocalDate()
                .atTime(6, 0)
                .atZone(zone)
                .toInstant()
        } else {
            now
        }
    }
}
