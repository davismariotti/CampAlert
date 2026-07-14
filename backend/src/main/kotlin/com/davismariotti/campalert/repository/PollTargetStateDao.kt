package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.PollTargetId
import com.davismariotti.campalert.model.TargetType
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.service.scheduling.PollTargetPhase
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

/**
 * Native-SQL claim/registration/cleanup operations for `poll_target_state` — a Spring Data JPA
 * repository can't express the FOR UPDATE SKIP LOCKED + UPDATE...FROM...RETURNING claim idiom, or
 * the ON CONFLICT DO NOTHING upsert used for idempotent target registration.
 */
@Repository
class PollTargetStateDao(
    private val jdbcTemplate: JdbcTemplate,
) {
    /**
     * Atomically claims every currently-due, unlocked target that still has at least one active
     * request, setting `locked_until` so no other claimer picks it up before [lockedUntil].
     */
    fun claimDue(now: Instant, lockedUntil: Instant): List<PollTargetId> =
        jdbcTemplate.query(
            CLAIM_SQL,
            { rs, _ ->
                PollTargetId(
                    targetType = TargetType.valueOf(rs.getString("target_type")),
                    provider = Provider.valueOf(rs.getString("provider")),
                    targetId = rs.getString("target_id"),
                )
            },
            Timestamp.from(lockedUntil),
            Timestamp.from(now),
            Timestamp.from(now),
        )

    /** Idempotent — inserts a fresh row with a deterministic phase offset only if one doesn't already exist for this target. */
    fun ensureTarget(
        targetType: TargetType,
        provider: Provider,
        targetId: String,
        intervalMs: Long
    ) {
        val phaseOffsetMs = PollTargetPhase.phaseOffsetMs(targetType, provider, targetId, intervalMs)
        val now = Instant.now()
        jdbcTemplate.update(
            """
            INSERT INTO poll_target_state (target_type, provider, target_id, phase_offset_ms, next_due_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (target_type, provider, target_id) DO NOTHING
            """.trimIndent(),
            targetType.name,
            provider.name,
            targetId,
            phaseOffsetMs.toInt(),
            Timestamp.from(now.plusMillis(phaseOffsetMs)),
        )
    }

    /** Deletes rows with no active request that haven't been due/touched since [now] minus [thresholdMs]. Returns rows deleted. */
    fun deleteStaleOrphans(now: Instant, thresholdMs: Long): Int {
        val cutoff = Timestamp.from(now.minusMillis(thresholdMs))
        return jdbcTemplate.update(DELETE_STALE_SQL, cutoff, Timestamp.from(now))
    }

    companion object {
        private val ACTIVE_REQUEST_EXISTS =
            """
            (
              (p.target_type = 'CAMPGROUND' AND EXISTS (
                  SELECT 1 FROM search_requests sr
                  JOIN search_request_state srs ON srs.search_request_id = sr.id
                  WHERE sr.campsite_id::text = p.target_id
                    AND sr.provider = p.provider
                    AND sr.user_id IS NOT NULL
                    AND srs.completed = false
                    AND srs.pause_reason IS NULL
              ))
              OR
              (p.target_type = 'PERMIT' AND EXISTS (
                  SELECT 1 FROM permit_search_requests psr
                  JOIN permit_search_request_state psrs ON psrs.permit_search_request_id = psr.id
                  WHERE psr.permit_id = p.target_id
                    AND psr.provider = p.provider
                    AND psr.user_id IS NOT NULL
                    AND psrs.completed = false
                    AND psrs.pause_reason IS NULL
              ))
            )
            """.trimIndent()

        private val CLAIM_SQL =
            """
            UPDATE poll_target_state pts
            SET locked_until = ?
            FROM (
                SELECT p.target_type, p.provider, p.target_id
                FROM poll_target_state p
                WHERE p.next_due_at <= ?
                  AND (p.locked_until IS NULL OR p.locked_until <= ?)
                  AND $ACTIVE_REQUEST_EXISTS
                FOR UPDATE SKIP LOCKED
            ) due
            WHERE pts.target_type = due.target_type AND pts.provider = due.provider AND pts.target_id = due.target_id
            RETURNING pts.target_type, pts.provider, pts.target_id
            """.trimIndent()

        private val DELETE_STALE_SQL =
            """
            DELETE FROM poll_target_state p
            WHERE COALESCE(p.last_finished_at, p.next_due_at) < ?
              AND (p.locked_until IS NULL OR p.locked_until < ?)
              AND NOT $ACTIVE_REQUEST_EXISTS
            """.trimIndent()
    }
}
