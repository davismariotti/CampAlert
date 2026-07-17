package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.service.redis.RedisJsonCache
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.TimeUnit

/** Normalized `{total, remaining}` for one quota gate on one date — see [ZoneAvailabilityBaselineService]. */
data class AvailabilityQuotaGate(
    val total: Int,
    val remaining: Int
)

data class ZoneBaselineSnapshot(
    val confirmed: Map<String, Map<String, AvailabilityQuotaGate>> = emptyMap(),
    val pending: Map<String, Map<String, AvailabilityQuotaGate>> = emptyMap(),
)

/**
 * Detects one specific implausible transition in permit availability: a (date, quota gate) pair that
 * showed real depletion (remaining < total) on the last CONFIRMED tick suddenly reporting remaining ==
 * total, as if every booking for that date vanished. Confirmed live on the zone endpoint: a batch of
 * Desolation Wilderness divisions showed exactly this pattern and self-corrected — sometimes within one
 * ~2-minute tick, sometimes spanning a couple of ticks — with no change on our end — almost certainly a
 * Recreation.gov-side sync lag, not a real mass cancellation.
 *
 * Shared by both ZONE and TRAILHEAD (design.md decision 7) via a normalized
 * `Map<String, AvailabilityQuotaGate>` per date: ZONE's single `{total, remaining}` field is wrapped as
 * a one-entry map at its call site in [PermitAvailabilityMatcher]; TRAILHEAD's already-open quota-type
 * map is passed straight through, so a multi-gate cell (e.g. Enchantments' `constant_quota_usage_daily`
 * + `quota_usage_by_member_daily`) gets each gate checked independently rather than just one. Still
 * named for its zone origin rather than renamed wholesale — the underlying detection logic and cache
 * key scheme are unchanged, only the shape of what it compares.
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
 * Also requires a gate's [AvailabilityQuotaGate.total] to be unchanged between ticks before flagging a
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
    /** True if any (date, gate) pair in [current] flipped from partially-booked to fully-open since the last CONFIRMED tick; a non-suspicious reading promotes straight to the confirmed baseline, while a suspicious one only promotes once it repeats on the following tick. */
    fun looksSuspicious(
        permitId: String,
        divisionId: String,
        month: YearMonth,
        current: Map<LocalDate, Map<String, AvailabilityQuotaGate>>,
    ): Boolean {
        val key = cacheKey(permitId, divisionId, month)
        val previous = redisJsonCache.get(key, ZoneBaselineSnapshot::class.java)
        val confirmed = previous?.confirmed ?: emptyMap()

        val suspicious = previous != null &&
            current.any { (date, gates) ->
                val confirmedGates = confirmed[date.toString()] ?: return@any false
                gates.any { (gateName, gate) ->
                    val confirmedGate = confirmedGates[gateName]
                    confirmedGate != null &&
                        confirmedGate.total == gate.total &&
                        confirmedGate.remaining < confirmedGate.total &&
                        gate.remaining == gate.total
                }
            }

        val currentByKey = current.mapKeys { it.key.toString() }
        val nextConfirmed = if (!suspicious || previous?.pending == currentByKey) currentByKey else confirmed
        redisJsonCache.set(key, ZoneBaselineSnapshot(confirmed = nextConfirmed, pending = currentByKey), ttlMinutes, TimeUnit.MINUTES)

        return suspicious
    }

    private fun cacheKey(permitId: String, divisionId: String, month: YearMonth) = "permit:zone-baseline:$permitId:$divisionId:$month"
}
