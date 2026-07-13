package com.davismariotti.campalert.service.state

import com.davismariotti.campalert.model.AvailabilityState
import com.davismariotti.campalert.model.NotificationOutbox
import com.davismariotti.campalert.model.OutboxType
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.repository.NotificationOutboxRepository
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
        val state = request.state
        val hasAvailable = result.hasAvailableSites
        val currentState = state.lastAvailabilityState
        val newState = if (hasAvailable) AvailabilityState.AVAILABLE else AvailabilityState.UNAVAILABLE

        state.totalChecks++
        if (hasAvailable) state.availableChecks++

        if (!hasAvailable && currentState == AvailabilityState.AVAILABLE) {
            val becameAt = state.becameAvailableAt
            if (becameAt != null) {
                state.windowCount++
                state.totalWindowSeconds += Duration.between(becameAt, now).seconds.toInt()
                state.becameAvailableAt = null
            }
        } else if (hasAvailable && (currentState == null || currentState == AvailabilityState.UNAVAILABLE)) {
            state.becameAvailableAt = now
        }

        val outboxType: OutboxType? = when {
            (currentState == null || currentState == AvailabilityState.UNAVAILABLE) && hasAvailable -> OutboxType.AVAILABLE
            currentState == AvailabilityState.AVAILABLE && !hasAvailable -> OutboxType.UNAVAILABLE
            currentState == AvailabilityState.AVAILABLE && hasAvailable -> {
                when {
                    state.userPaused -> null
                    state.reminderSentAt != null -> null
                    state.lastNotifiedAt == null -> null
                    Duration.between(state.lastNotifiedAt, now) > Duration.ofMinutes(30) -> OutboxType.REMINDER
                    else -> null
                }
            }
            else -> null
        }

        state.lastAvailabilityState = newState
        if (currentState == AvailabilityState.AVAILABLE && !hasAvailable) {
            state.userPaused = false
            state.reminderSentAt = null
        }
        if (outboxType == OutboxType.REMINDER) {
            state.reminderSentAt = now
        }
        searchRequestRepository.save(request)

        if (outboxType != null) {
            log.info(
                "Availability transition requestId={} campsiteId={} from={} to={} availableSites={} outboxType={}",
                request.id,
                request.campsiteId,
                currentState,
                newState,
                result.availableSiteCount,
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
                    "availableSiteCount" to result.availableSiteCount,
                ),
            )

            val sendAfter = computeSendAfter(now, user.timezone)
            notificationOutboxRepository.save(
                NotificationOutbox(
                    userId = user.id!!,
                    requestId = request.id!!,
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
