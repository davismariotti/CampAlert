package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.provider.recreation.PermitZoneAvailabilityCell
import com.davismariotti.campalert.service.redis.RedisJsonCache
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

data class ZoneBaselineSnapshot(
    val confirmed: Map<String, PermitZoneAvailabilityCell> = emptyMap(),
    val pending: Map<String, PermitZoneAvailabilityCell> = emptyMap(),
)

/**
 * Detects one specific implausible transition in zone permit availability: a date that showed real
 * depletion (remaining < total) on the last CONFIRMED tick suddenly reporting remaining == total, as
 * if every booking for that date vanished. Confirmed live: a batch of Desolation Wilderness divisions
 * showed exactly this pattern and self-corrected — sometimes within one ~2-minute tick, sometimes
 * spanning a couple of ticks — with no change on our end — almost certainly a Recreation.gov-side sync
 * lag, not a real mass cancellation.
 *
 * An ordinary (non-suspicious) reading promotes straight to [ZoneBaselineSnapshot.confirmed], same as
 * before — that's what lets a division's very first-ever tick establish a real baseline, and lets a
 * legitimately-changing baseline (e.g. real bookings depleting a quota) stay current tick to tick. Only
 * a reading that trips the suspicious check is held back: [ZoneBaselineSnapshot.pending] tracks it, and
 * it's only promoted to `confirmed` once the *same* reading repeats on the very next tick. Without that
 * hold-back, a division flagged suspicious on tick N would have its ghost reading written back as the
 * trusted baseline immediately, so tick N+1 would compare the ghost data against itself and pass it as
 * legitimate the moment the glitch outlasted a single tick — which is exactly what let a false
 * "available" notification through. This costs a genuine flip a single extra ~2-minute confirmation
 * tick, but keeps flagging a still-unconfirmed ghost reading on every tick until it reverts or holds.
 *
 * Deliberately NOT a blanket "remaining==total everywhere" check: permits with a defined off-season
 * (e.g. Desolation's HighUseSeasonEnd rule) legitimately show every division wide open with nothing
 * left to decrement once quotas lift. Comparing only against this division's OWN confirmed baseline —
 * never against siblings or an absolute threshold — avoids flagging that as suspicious.
 *
 * Also requires [PermitZoneAvailabilityCell.total] to be unchanged between ticks before flagging a
 * flip. Confirmed live: dates past a division's permit season report a sentinel total (e.g. 900000,
 * with remaining == total) instead of the real permit quota — a legitimate change in what's being
 * counted, not bookings vanishing against the same quota. Requiring the total to match keeps that
 * season-boundary transition from being flagged, while still catching a same-quota reset (e.g.
 * total=25 both ticks, remaining jumping 3 -> 25), which is the actual observed glitch pattern.
 */
@Service
class ZoneAvailabilityBaselineService(
    private val redisJsonCache: RedisJsonCache,
    @param:Value("\${campfinder.permit.zone-baseline-ttl-minutes:30}") private val ttlMinutes: Long,
) {
    /** True if any date in [current] flipped from partially-booked to fully-open since the last CONFIRMED tick; a non-suspicious reading promotes straight to the confirmed baseline, while a suspicious one only promotes once it repeats on the following tick. */
    fun looksSuspicious(
        permitId: String,
        divisionId: String,
        month: YearMonth,
        current: Map<ZonedDateTime, PermitZoneAvailabilityCell>,
    ): Boolean {
        val key = cacheKey(permitId, divisionId, month)
        val previous = redisJsonCache.get(key, ZoneBaselineSnapshot::class.java)
        val confirmed = previous?.confirmed ?: emptyMap()

        val suspicious = previous != null &&
            current.any { (dateTime, cell) ->
                val confirmedCell = confirmed[dateTime.toString()]
                confirmedCell != null &&
                    confirmedCell.total == cell.total &&
                    confirmedCell.remaining < confirmedCell.total &&
                    cell.remaining == cell.total
            }

        val currentByKey = current.mapKeys { it.key.toString() }
        val nextConfirmed = if (!suspicious || previous?.pending == currentByKey) currentByKey else confirmed
        redisJsonCache.set(key, ZoneBaselineSnapshot(confirmed = nextConfirmed, pending = currentByKey), ttlMinutes, TimeUnit.MINUTES)

        return suspicious
    }

    private fun cacheKey(permitId: String, divisionId: String, month: YearMonth) = "permit:zone-baseline:$permitId:$divisionId:$month"
}
