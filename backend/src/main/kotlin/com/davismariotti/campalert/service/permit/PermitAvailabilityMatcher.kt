package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.model.AvailabilityState
import com.davismariotti.campalert.model.PermitSearchRequest
import com.davismariotti.campalert.model.SearchType
import com.davismariotti.campalert.provider.CallProtection
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.provider.recreation.PermitItineraryAvailabilityPayload
import com.davismariotti.campalert.provider.recreation.PermitQuotaType
import com.davismariotti.campalert.provider.recreation.PermitTrailheadAvailabilityCell
import com.davismariotti.campalert.provider.recreation.PermitZoneAvailabilityPayload
import com.davismariotti.campalert.provider.recreation.PermitZoneDivisionAvailability
import com.davismariotti.campalert.provider.recreation.RawResponseCapture
import com.davismariotti.campalert.provider.recreation.RecreationApi
import com.davismariotti.campalert.util.sleepJitter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/** [rawJson] is the literal Recreation.gov response body, captured alongside the parsed [payload] for debugging. */
data class ZoneMonthFetch(
    val payload: PermitZoneAvailabilityPayload?,
    val rawJson: String?
)

typealias ZoneAvailabilityCache = ConcurrentHashMap<Pair<String, YearMonth>, CompletableFuture<ZoneMonthFetch>>
typealias ItineraryAvailabilityCache = ConcurrentHashMap<Triple<String, String, YearMonth>, CompletableFuture<PermitItineraryAvailabilityPayload?>>

/** [rawJson] is the literal Recreation.gov response body for the trailhead availability fetch — see [ZoneMonthFetch]. */
data class TrailheadMonthFetch(
    val payload: Map<LocalDate, Map<String, PermitTrailheadAvailabilityCell>>?,
    val rawJson: String?,
)

typealias TrailheadAvailabilityCache = ConcurrentHashMap<Pair<String, YearMonth>, CompletableFuture<TrailheadMonthFetch>>

/** One target division's evaluation for a given month, computed once per (request, month) to avoid double-calling [ZoneAvailabilityBaselineService.looksSuspicious] for the same key. */
private data class DivisionEvaluation(
    val divisionId: String,
    val division: PermitZoneDivisionAvailability,
    val isContradictory: Boolean,
    val looksSuspiciousVsBaseline: Boolean,
)

/** One target division's evaluation for a trailhead permit for a given month — see [DivisionEvaluation]. */
private data class TrailheadDivisionEvaluation(
    val divisionId: String,
    val cellsByDate: Map<LocalDate, PermitTrailheadAvailabilityCell>,
    val looksSuspiciousVsBaseline: Boolean,
)

/**
 * Implements the permit matching semantics from design decision 3: zone and trailhead are both
 * OR-across-accepted-divisions-and-dates-in-window (trailhead additionally requires every quota-type
 * gate present on a matched cell to independently clear, and never matches a `not_yet_released` cell —
 * see `checkTrailhead`); itinerary is AND-across-every-ordered-leg. All caches are scoped to one check
 * cycle and passed in by the caller (see [PermitPollCheckService]), mirroring
 * [com.davismariotti.campalert.provider.recreation.RecreationServiceImpl]'s `(campsiteId, YearMonth)`
 * dedup cache — one fetch per (permit, month) or (permit, division, month) across every request being
 * processed in a cycle, not one per request.
 */
@Service
class PermitAvailabilityMatcher(
    private val recreationApi: RecreationApi,
    @Qualifier("recreationGovCallProtection") private val callProtection: CallProtection,
    private val zoneAvailabilityBaselineService: ZoneAvailabilityBaselineService,
    @param:Value($$"${campfinder.polling.request-jitter-ms:0}") private val requestJitterMs: Long = 0,
) : PermitAvailabilityProvider {
    private val log = LoggerFactory.getLogger(javaClass)

    override val provider = Provider.RECREATION_GOV

    override fun check(
        request: PermitSearchRequest,
        zoneCache: ZoneAvailabilityCache,
        itineraryCache: ItineraryAvailabilityCache,
        trailheadCache: TrailheadAvailabilityCache,
    ): PermitAvailabilityResult =
        when (request.searchType) {
            SearchType.ZONE -> checkZone(request, zoneCache)
            SearchType.ITINERARY -> checkItinerary(request, itineraryCache)
            SearchType.TRAILHEAD -> checkTrailhead(request, trailheadCache)
        }

    private fun checkZone(request: PermitSearchRequest, cache: ZoneAvailabilityCache): PermitAvailabilityResult {
        val target = request.zoneTarget
        if (target == null || target.divisionIds.isEmpty()) {
            return PermitAvailabilityResult(request, hasAvailability = false)
        }

        var monthStart = target.startDay.withDayOfMonth(1)
        val endMonth = YearMonth.from(target.endDay)
        while (!YearMonth.from(monthStart).isAfter(endMonth)) {
            val month = YearMonth.from(monthStart)
            val fetch = fetchZoneMonth(request.permitId, month, cache)
            val payload = fetch.payload
            if (payload != null) {
                // Always evaluated (and always records this tick as the new pending baseline reading)
                // for every target division up front, so the baseline stays fresh every tick even for
                // divisions later skipped, and so correlatedGlitch below can see every division's result.
                val evaluated = target.divisionIds.mapNotNull { divisionId ->
                    val division = payload.availability[divisionId] ?: return@mapNotNull null

                    val looksSuspiciousVsBaseline = zoneAvailabilityBaselineService.looksSuspicious(
                        request.permitId,
                        divisionId,
                        month,
                        division.dateAvailability.entries.associate { (dateTime, cell) ->
                            dateTime.toLocalDate() to mapOf(ZONE_QUOTA_GATE to AvailabilityQuotaGate(cell.total, cell.remaining))
                        },
                    )

                    val cutoff = payload.nextAvailableDate
                    val isContradictory = cutoff != null &&
                        division.dateAvailability.any { (dateTime, cell) -> dateTime.isBefore(cutoff) && cell.remaining > 0 }

                    DivisionEvaluation(divisionId, division, isContradictory, looksSuspiciousVsBaseline)
                }

                // Confirmed live: a Recreation.gov sync glitch hit a rotating batch of divisions in the
                // same response at once (17/47, then later 2/47, never the same set) rather than one
                // broken division. Several sibling divisions independently flagged in the same tick is
                // itself evidence the whole response is untrustworthy this tick, even for a division
                // that individually looked clean.
                val correlatedGlitch = evaluated.count { it.isContradictory || it.looksSuspiciousVsBaseline } >= MIN_CORRELATED_SUSPICIOUS_DIVISIONS

                for (evaluation in evaluated) {
                    if (evaluation.isContradictory || evaluation.looksSuspiciousVsBaseline || correlatedGlitch) {
                        log.warn(
                            "Skipping zone permit division with untrustworthy availability permitId={} divisionId={} contradictory={} suspiciousVsBaseline={} correlatedGlitch={} nextAvailableDate={}",
                            request.permitId,
                            evaluation.divisionId,
                            evaluation.isContradictory,
                            evaluation.looksSuspiciousVsBaseline,
                            correlatedGlitch,
                            payload.nextAvailableDate,
                        )
                        continue
                    }

                    val match = evaluation.division.dateAvailability.entries
                        .filter { (dateTime, _) ->
                            val date = dateTime.toLocalDate()
                            !date.isBefore(target.startDay) && !date.isAfter(target.endDay)
                        }.sortedBy { it.key }
                        // Zone quotas are PAX-based (Recreation.gov's `total`/`remaining` count people,
                        // not permit slots), so a cell only fits this request if it can seat the whole group.
                        .firstOrNull { it.value.remaining >= request.groupSize }
                    if (match != null) {
                        val matchedDate = match.key.toLocalDate()

                        // Only worth the extra call when this candidate would actually create a fresh
                        // "available" notification — steady-state AVAILABLE ticks (including reminders)
                        // already trusted the transition once and don't need re-verifying every 2 minutes.
                        val isFreshTransition = request.state.lastAvailabilityState != AvailabilityState.AVAILABLE
                        if (isFreshTransition && !corroborateMatch(request.permitId, evaluation.divisionId, matchedDate, request.groupSize)) {
                            log.warn(
                                "Zone permit match failed independent corroboration permitId={} divisionId={} date={}",
                                request.permitId,
                                evaluation.divisionId,
                                matchedDate,
                            )
                            continue
                        }

                        log.debug(
                            "Zone permit availability match permitId={} divisionId={} matchedDate={} response={}",
                            request.permitId,
                            evaluation.divisionId,
                            matchedDate,
                            fetch.rawJson,
                        )
                        return PermitAvailabilityResult(
                            request,
                            hasAvailability = true,
                            matchedDivisionId = evaluation.divisionId,
                            matchedDate = matchedDate,
                        )
                    }
                }
            }
            monthStart = monthStart.plusMonths(1)
        }
        return PermitAvailabilityResult(request, hasAvailability = false)
    }

    /**
     * Corroborates a candidate match against [RecreationApi.getDivisionAvailability] — a single-division
     * endpoint confirmed live to be computed independently from the multi-division zone endpoint, not
     * just a slice of the same cached payload. Fails closed (treats an error or a disagreeing response
     * as "not corroborated") since a false negative here only delays a real notification by one poll,
     * while a false positive is the exact bug this exists to prevent.
     */
    private fun corroborateMatch(
        permitId: String,
        divisionId: String,
        date: LocalDate,
        groupSize: Int
    ): Boolean {
        sleepJitter(requestJitterMs)
        return try {
            callProtection.execute {
                val startDate = date.atStartOfDay().atZone(ZoneOffset.UTC).format(dateFormatter)
                val endDate = date
                    .plusDays(1)
                    .atStartOfDay()
                    .atZone(ZoneOffset.UTC)
                    .format(dateFormatter)
                val response = recreationApi.getDivisionAvailability(permitId, divisionId, startDate, endDate).execute()
                val cell = response
                    .body()
                    ?.payload
                    ?.dateAvailability
                    ?.entries
                    ?.firstOrNull { it.key.toLocalDate() == date }
                    ?.value
                cell != null && cell.remaining >= groupSize
            }
        } catch (e: Exception) {
            log.warn("Failed to corroborate permit division availability permitId={} divisionId={} date={}", permitId, divisionId, date, e)
            false
        }
    }

    private fun fetchZoneMonth(permitId: String, month: YearMonth, cache: ZoneAvailabilityCache): ZoneMonthFetch =
        cache
            .computeIfAbsent(Pair(permitId, month)) {
                CompletableFuture.supplyAsync { fetchZoneMonthDirect(permitId, month) }
            }.get()

    private fun fetchZoneMonthDirect(permitId: String, month: YearMonth): ZoneMonthFetch {
        sleepJitter(requestJitterMs)
        return try {
            callProtection.execute {
                val startDate = month
                    .atDay(1)
                    .atStartOfDay()
                    .atZone(ZoneOffset.UTC)
                    .format(dateFormatter)
                val response = recreationApi.getZonePermitAvailability(permitId, startDate).execute()
                val rawJson = RawResponseCapture.takeAndClear()
                ZoneMonthFetch(payload = response.body()?.payload, rawJson = rawJson)
            }
        } catch (e: Exception) {
            log.warn("Failed to fetch zone permit availability permitId={} month={}", permitId, month, e)
            ZoneMonthFetch(payload = null, rawJson = null)
        }
    }

    private fun checkItinerary(request: PermitSearchRequest, cache: ItineraryAvailabilityCache): PermitAvailabilityResult {
        val legs = request.itineraryTarget?.legs
        if (legs.isNullOrEmpty()) {
            return PermitAvailabilityResult(request, hasAvailability = false)
        }

        for (leg in legs) {
            val month = YearMonth.from(leg.date)
            val payload = fetchItineraryMonth(request.permitId, leg.divisionId, month, cache)
            val quotaMaps = payload?.quotaTypeMaps ?: emptyMap()
            // Every present quota type must have room on this date — Recreation.gov enforces each
            // one simultaneously (e.g. a constant per-day quota and a per-member quota). Only
            // ConstantQuotaUsageDaily gates a single flat permit slot regardless of group size;
            // QuotaUsageByMemberDaily (and UNKNOWN, since an unrecognized quota type's semantics
            // aren't known) are treated as PAX-based and need room for the whole group — safer to
            // under-alert than to alert on a slot too small to book.
            val available = quotaMaps.isNotEmpty() &&
                quotaMaps.all { (type, byDate) ->
                    val needed = if (type == PermitQuotaType.ConstantQuotaUsageDaily) 1 else request.groupSize
                    (byDate[leg.date]?.remaining ?: 0) >= needed
                }
            if (!available) {
                return PermitAvailabilityResult(
                    request,
                    hasAvailability = false,
                    blockingDivisionId = leg.divisionId,
                    blockingDate = leg.date,
                )
            }
        }
        return PermitAvailabilityResult(request, hasAvailability = true)
    }

    private fun fetchItineraryMonth(
        permitId: String,
        divisionId: String,
        month: YearMonth,
        cache: ItineraryAvailabilityCache,
    ): PermitItineraryAvailabilityPayload? =
        cache
            .computeIfAbsent(Triple(permitId, divisionId, month)) {
                CompletableFuture.supplyAsync { fetchItineraryMonthDirect(permitId, divisionId, month) }
            }.get()

    private fun fetchItineraryMonthDirect(permitId: String, divisionId: String, month: YearMonth): PermitItineraryAvailabilityPayload? {
        sleepJitter(requestJitterMs)
        return try {
            callProtection.execute {
                recreationApi
                    .getItineraryDivisionAvailability(permitId, divisionId, month.monthValue, month.year)
                    .execute()
                    .body()
                    ?.payload
            }
        } catch (e: Exception) {
            log.warn("Failed to fetch itinerary permit availability permitId={} divisionId={} month={}", permitId, divisionId, month, e)
            null
        }
    }

    /**
     * OR-across-accepted-divisions-and-dates-in-window, same as [checkZone] — plus two rules unique to
     * the open quota-gate shape (design.md decisions 3 and 4): a cell only matches when every quota-type
     * gate present on it independently clears (see [cellClears]), and a `not_yet_released` cell never
     * matches regardless of any number present on it. A division missing entirely from a date's response
     * is simply never iterated, which is the same "unavailable" outcome by construction, not a special case.
     */
    private fun checkTrailhead(request: PermitSearchRequest, cache: TrailheadAvailabilityCache): PermitAvailabilityResult {
        val target = request.trailheadTarget
        if (target == null || target.divisionIds.isEmpty()) {
            return PermitAvailabilityResult(request, hasAvailability = false)
        }

        var monthStart = target.startDay.withDayOfMonth(1)
        val endMonth = YearMonth.from(target.endDay)
        while (!YearMonth.from(monthStart).isAfter(endMonth)) {
            val month = YearMonth.from(monthStart)
            val fetch = fetchTrailheadMonth(request.permitId, month, cache)
            val payload = fetch.payload
            if (payload != null) {
                // Same up-front-evaluation shape as checkZone: every target division is evaluated (and
                // records this tick as the new pending baseline reading) before any match is trusted, so
                // the baseline stays fresh every tick and correlatedGlitch below can see every division.
                val evaluated = target.divisionIds.map { divisionId ->
                    val cellsByDate = payload.entries
                        .mapNotNull { (date, divisions) -> divisions[divisionId]?.let { date to it } }
                        .toMap()

                    val looksSuspiciousVsBaseline = zoneAvailabilityBaselineService.looksSuspicious(
                        request.permitId,
                        divisionId,
                        month,
                        cellsByDate.mapValues { (_, cell) ->
                            cell.quotaGates.mapValues { (_, gate) -> AvailabilityQuotaGate(gate.total, gate.remaining) }
                        },
                    )

                    TrailheadDivisionEvaluation(divisionId, cellsByDate, looksSuspiciousVsBaseline)
                }

                val correlatedGlitch = evaluated.count { it.looksSuspiciousVsBaseline } >= MIN_CORRELATED_SUSPICIOUS_DIVISIONS

                for (evaluation in evaluated) {
                    if (evaluation.looksSuspiciousVsBaseline || correlatedGlitch) {
                        log.warn(
                            "Skipping trailhead permit division with untrustworthy availability permitId={} divisionId={} suspiciousVsBaseline={} correlatedGlitch={}",
                            request.permitId,
                            evaluation.divisionId,
                            evaluation.looksSuspiciousVsBaseline,
                            correlatedGlitch,
                        )
                        continue
                    }

                    val match = evaluation.cellsByDate.entries
                        .filter { (date, _) -> !date.isBefore(target.startDay) && !date.isAfter(target.endDay) }
                        .sortedBy { it.key }
                        .firstOrNull { (_, cell) -> cellClears(cell, request.groupSize) }
                    if (match != null) {
                        val matchedDate = match.key

                        // Only worth the extra call when this candidate would actually create a fresh
                        // "available" notification — same reasoning as checkZone's corroborateMatch.
                        val isFreshTransition = request.state.lastAvailabilityState != AvailabilityState.AVAILABLE
                        if (isFreshTransition &&
                            !corroborateTrailheadMatch(request.permitId, evaluation.divisionId, matchedDate, request.groupSize, match.value)
                        ) {
                            log.warn(
                                "Trailhead permit match failed independent corroboration permitId={} divisionId={} date={}",
                                request.permitId,
                                evaluation.divisionId,
                                matchedDate,
                            )
                            continue
                        }

                        log.debug(
                            "Trailhead permit availability match permitId={} divisionId={} matchedDate={} response={}",
                            request.permitId,
                            evaluation.divisionId,
                            matchedDate,
                            fetch.rawJson,
                        )
                        return PermitAvailabilityResult(
                            request,
                            hasAvailability = true,
                            matchedDivisionId = evaluation.divisionId,
                            matchedDate = matchedDate,
                        )
                    }
                }
            }
            monthStart = monthStart.plusMonths(1)
        }
        return PermitAvailabilityResult(request, hasAvailability = false)
    }

    /**
     * A cell matches only when it isn't `not_yet_released` and every quota-type gate present on it
     * independently clears its required amount: 1 for [FLAT_QUOTA_GATE] (a flat per-permit slot,
     * regardless of group size — confirmed live on Enchantments), `groupSize` for every other gate name,
     * including one this codebase has never seen before — fail closed the same way the itinerary matcher
     * already treats an unrecognized [PermitQuotaType] as PAX-based. A cell with no gates at all never
     * matches (an empty map's `all {}` is vacuously true, which would be wrong here).
     */
    private fun cellClears(cell: PermitTrailheadAvailabilityCell, groupSize: Int): Boolean =
        !cell.notYetReleased &&
            cell.quotaGates.isNotEmpty() &&
            cell.quotaGates.all { (gateName, gate) ->
                val needed = if (gateName == FLAT_QUOTA_GATE) 1 else groupSize
                gate.remaining >= needed
            }

    /**
     * Corroborates a candidate trailhead match against [RecreationApi.getTrailheadDivisionAvailability]
     * — see design.md decision 7. Unlike [corroborateMatch]'s confirmed-independent
     * [RecreationApi.getDivisionAvailability], this call's independence from the bulk trailhead
     * response is NOT confirmed; used anyway as a knowingly-imperfect safety layer. Fails closed like
     * [corroborateMatch]. Logs any disagreement with the bulk cell — even one that doesn't change the
     * match outcome — to gather evidence on whether the two calls are actually computed independently.
     */
    private fun corroborateTrailheadMatch(
        permitId: String,
        divisionId: String,
        date: LocalDate,
        groupSize: Int,
        bulkCell: PermitTrailheadAvailabilityCell,
    ): Boolean {
        sleepJitter(requestJitterMs)
        return try {
            callProtection.execute {
                val month = YearMonth.from(date)
                val startDate = month.atDay(1).format(RecreationApi.trailheadDateFormatter)
                val endDate = month.atEndOfMonth().format(RecreationApi.trailheadDateFormatter)
                val response = recreationApi.getTrailheadDivisionAvailability(permitId, divisionId, startDate, endDate).execute()
                val corroborationCell = response
                    .body()
                    ?.payload
                    ?.get(date)
                    ?.get(divisionId)
                if (corroborationCell != bulkCell) {
                    log.warn(
                        "Trailhead corroboration cell disagreed with bulk response permitId={} divisionId={} date={} bulkCell={} corroborationCell={}",
                        permitId,
                        divisionId,
                        date,
                        bulkCell,
                        corroborationCell,
                    )
                }
                corroborationCell != null && cellClears(corroborationCell, groupSize)
            }
        } catch (e: Exception) {
            log.warn("Failed to corroborate trailhead permit division availability permitId={} divisionId={} date={}", permitId, divisionId, date, e)
            false
        }
    }

    private fun fetchTrailheadMonth(permitId: String, month: YearMonth, cache: TrailheadAvailabilityCache): TrailheadMonthFetch =
        cache
            .computeIfAbsent(Pair(permitId, month)) {
                CompletableFuture.supplyAsync { fetchTrailheadMonthDirect(permitId, month) }
            }.get()

    private fun fetchTrailheadMonthDirect(permitId: String, month: YearMonth): TrailheadMonthFetch {
        sleepJitter(requestJitterMs)
        return try {
            callProtection.execute {
                val startDate = month.atDay(1).format(RecreationApi.trailheadDateFormatter)
                val endDate = month.atEndOfMonth().format(RecreationApi.trailheadDateFormatter)
                val response = recreationApi.getTrailheadPermitAvailability(permitId, startDate, endDate).execute()
                val rawJson = RawResponseCapture.takeAndClear()
                TrailheadMonthFetch(payload = response.body()?.payload, rawJson = rawJson)
            }
        } catch (e: Exception) {
            log.warn("Failed to fetch trailhead permit availability permitId={} month={}", permitId, month, e)
            TrailheadMonthFetch(payload = null, rawJson = null)
        }
    }

    companion object {
        private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

        /** Minimum number of a request's target divisions independently flagged untrustworthy in the same tick before the whole response is treated as a correlated glitch. */
        private const val MIN_CORRELATED_SUSPICIOUS_DIVISIONS = 2

        /** Key ZONE's single `{total, remaining}` field is wrapped under when calling the generalized [ZoneAvailabilityBaselineService]. */
        private const val ZONE_QUOTA_GATE = "default"

        /** The one quota-gate name confirmed to be a flat, single-permit-slot gate (Enchantments) rather than PAX-based — see [cellClears]. */
        private const val FLAT_QUOTA_GATE = "constant_quota_usage_daily"
    }
}
