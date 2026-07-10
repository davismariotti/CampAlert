package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.model.AvailabilityState
import com.davismariotti.campalert.model.NotificationOutbox
import com.davismariotti.campalert.model.OutboxType
import com.davismariotti.campalert.model.RequestType
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.repository.NotificationOutboxRepository
import com.davismariotti.campalert.repository.PermitSearchRequestRepository
import com.davismariotti.campalert.service.state.AvailabilityStateService
import com.newrelic.api.agent.NewRelic
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/** Permit analogue of [AvailabilityStateService.processResult] — diffs state, tracks stats, writes PERMIT outbox rows. */
@Service
class PermitAvailabilityStateService(
    private val permitSearchRequestRepository: PermitSearchRequestRepository,
    private val notificationOutboxRepository: NotificationOutboxRepository,
    private val availabilityStateService: AvailabilityStateService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun processUserResults(results: List<PermitAvailabilityResult>, user: User) {
        val now = Instant.now()
        results.forEach { result -> processResult(result, user, now) }
    }

    private fun processResult(result: PermitAvailabilityResult, user: User, now: Instant) {
        val request = result.request
        val state = request.state
        val hasAvailable = result.hasAvailability
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
        // Populated as a byproduct of the matcher walking each division/leg; cleared whenever the
        // corresponding side of the match no longer applies (design decisions 9 / spec scenarios).
        state.matchedDivisionId = result.matchedDivisionId
        state.matchedDate = result.matchedDate
        state.blockingDivisionId = result.blockingDivisionId
        state.blockingDate = result.blockingDate
        if (currentState == AvailabilityState.AVAILABLE && !hasAvailable) {
            state.userPaused = false
            state.reminderSentAt = null
        }
        if (outboxType == OutboxType.REMINDER) {
            state.reminderSentAt = now
        }
        permitSearchRequestRepository.save(request)

        if (outboxType != null) {
            log.info(
                "Permit availability transition requestId={} permitId={} from={} to={} outboxType={}",
                request.id,
                request.permitId,
                currentState,
                newState,
                outboxType,
            )
            NewRelic.getAgent().insights.recordCustomEvent(
                "PermitAvailabilityStateChange",
                mapOf(
                    "userId" to user.id!!,
                    "requestId" to request.id!!,
                    "permitId" to request.permitId,
                    "permitName" to request.permitName,
                    "from" to (currentState?.name ?: "null"),
                    "to" to newState.name,
                ),
            )

            val sendAfter = availabilityStateService.computeSendAfter(now, user.timezone)
            notificationOutboxRepository.save(
                NotificationOutbox(
                    userId = user.id!!,
                    requestId = request.id!!,
                    requestType = RequestType.PERMIT,
                    type = outboxType,
                    sendAfter = sendAfter,
                ),
            )
        }
    }
}
