package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.recreation.PermitZoneAvailabilityCell
import com.davismariotti.campalert.service.redis.RedisJsonCache
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

data class ZoneBaselineSnapshot(
    val cells: Map<String, PermitZoneAvailabilityCell> = emptyMap()
)

/**
 * Detects one specific implausible transition in zone permit availability: a date that showed real
 * depletion (remaining < total) on the last poll suddenly reporting remaining == total, as if every
 * booking for that date vanished within a single ~2-minute tick. Confirmed live: a batch of
 * Desolation Wilderness divisions showed exactly this pattern and self-corrected within hours with
 * no change on our end — almost certainly a Recreation.gov-side sync lag, not a real mass
 * cancellation.
 *
 * Deliberately NOT a blanket "remaining==total everywhere" check: permits with a defined off-season
 * (e.g. Desolation's HighUseSeasonEnd rule) legitimately show every division wide open with nothing
 * left to decrement once quotas lift. Comparing only against this division's OWN previous tick — never
 * against siblings or an absolute threshold — avoids flagging that as suspicious.
 */
@Service
class ZoneAvailabilityBaselineService(
    private val redisJsonCache: RedisJsonCache,
    @param:Value("\${campfinder.permit.zone-baseline-ttl-minutes:30}") private val ttlMinutes: Long,
) {
    /** True if any date in [current] flipped from partially-booked to fully-open since the last recorded tick; always records [current] as the new baseline regardless of the result. */
    fun looksSuspicious(
        permitId: String,
        divisionId: String,
        month: YearMonth,
        current: Map<ZonedDateTime, PermitZoneAvailabilityCell>,
    ): Boolean {
        val key = cacheKey(permitId, divisionId, month)
        val previous = redisJsonCache.get(key, ZoneBaselineSnapshot::class.java)

        val suspicious = previous != null &&
            current.any { (dateTime, cell) ->
                val previousCell = previous.cells[dateTime.toString()]
                previousCell != null && previousCell.remaining < previousCell.total && cell.remaining == cell.total
            }

        val snapshot = ZoneBaselineSnapshot(current.mapKeys { it.key.toString() })
        redisJsonCache.set(key, snapshot, ttlMinutes, TimeUnit.MINUTES)

        return suspicious
    }

    private fun cacheKey(permitId: String, divisionId: String, month: YearMonth) = "permit:zone-baseline:$permitId:$divisionId:$month"
}
