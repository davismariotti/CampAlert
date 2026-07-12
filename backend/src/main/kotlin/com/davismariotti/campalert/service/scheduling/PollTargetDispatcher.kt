package com.davismariotti.campalert.service.scheduling

import com.davismariotti.campalert.model.PollCheckStatus
import com.davismariotti.campalert.model.PollTargetId
import com.davismariotti.campalert.model.TargetType
import com.davismariotti.campalert.repository.PollTargetStateDao
import com.davismariotti.campalert.repository.PollTargetStateRepository
import com.davismariotti.campalert.service.permit.PermitPollCheckService
import com.newrelic.api.agent.NewRelic
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import kotlin.random.Random

/**
 * Claims due `poll_target_state` rows every tick and dispatches each claimed target's check
 * (campground or permit) onto the shared executor — the sole driver of campground/permit
 * availability checks (see poll-target-scheduling spec). Replaces the old global `Scheduler` tick
 * for both campgrounds and permits.
 */
@Component
class PollTargetDispatcher(
    private val pollTargetStateDao: PollTargetStateDao,
    private val pollTargetStateRepository: PollTargetStateRepository,
    private val campgroundPollCheckService: CampgroundPollCheckService,
    private val permitPollCheckService: PermitPollCheckService,
    @Qualifier("availabilityCheckerExecutor") private val executor: Executor,
    @param:Value($$"${campfinder.polling.interval-ms}") private val intervalMs: Long,
    @param:Value($$"${campfinder.polling.tick-jitter-ms:0}") private val phaseJitterMs: Long,
    @param:Value($$"${campfinder.polling.claim-lease-ms:180000}") private val claimLeaseMs: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = $$"${campfinder.polling.dispatcher-interval-ms:5000}")
    fun dispatch() {
        val now = Instant.now()
        val claimed = pollTargetStateDao.claimDue(now, now.plusMillis(claimLeaseMs))
        if (claimed.isEmpty()) return

        log.info("Poll dispatcher claimed targets count={}", claimed.size)
        claimed.forEach { id ->
            CompletableFuture.runAsync({ processTarget(id) }, executor)
        }
    }

    private fun processTarget(id: PollTargetId) {
        val startedAt = Instant.now()
        var status = PollCheckStatus.SUCCESS
        var error: String? = null
        var requestsEvaluated = 0
        try {
            requestsEvaluated = when (id.targetType) {
                TargetType.CAMPGROUND -> campgroundPollCheckService.check(id.targetId.toInt())
                TargetType.PERMIT -> permitPollCheckService.check(id.targetId)
            }
        } catch (e: Exception) {
            status = PollCheckStatus.FAILURE
            error = e.message?.take(1000)
            log.error("Poll target check failed targetType={} targetId={}", id.targetType, id.targetId, e)
        }
        val finishedAt = Instant.now()

        NewRelic.getAgent().insights.recordCustomEvent(
            "PollTargetCheckCompleted",
            mapOf(
                "targetType" to id.targetType.name,
                "targetId" to id.targetId,
                "status" to status.name,
                "durationMs" to Duration.between(startedAt, finishedAt).toMillis(),
                "requestsEvaluated" to requestsEvaluated,
            ),
        )

        updateAfterCheck(id, startedAt, finishedAt, status, error)
    }

    private fun updateAfterCheck(
        id: PollTargetId,
        startedAt: Instant,
        finishedAt: Instant,
        status: PollCheckStatus,
        error: String?
    ) {
        val existing = pollTargetStateRepository.findById(id).orElse(null) ?: return
        val jitter = if (phaseJitterMs > 0) Random.nextLong(phaseJitterMs + 1) else 0
        val nextDueAt = finishedAt.plusMillis(intervalMs).plusMillis(jitter)
        pollTargetStateRepository.save(
            existing.copy(
                nextDueAt = nextDueAt,
                lockedUntil = null,
                lastStartedAt = startedAt,
                lastFinishedAt = finishedAt,
                lastStatus = status,
                lastError = error,
            ),
        )
    }
}
