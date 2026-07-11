package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.model.PermitSearchRequest
import com.davismariotti.campalert.model.SearchType
import com.davismariotti.campalert.recreation.PermitItineraryAvailabilityPayload
import com.davismariotti.campalert.recreation.PermitZoneAvailabilityPayload
import com.davismariotti.campalert.recreation.RawResponseCapture
import com.davismariotti.campalert.recreation.RecreationApi
import com.davismariotti.campalert.util.sleepJitter
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
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

/**
 * Implements the two permit matching semantics from design decision 3: zone is OR-across-accepted-
 * divisions-and-dates-in-window; itinerary is AND-across-every-ordered-leg. Both caches are tick-scoped
 * and passed in by the caller (see [PermitAvailabilityChecker]), mirroring [com.davismariotti.campalert.service.availability.RecreationServiceImpl]'s
 * `(campsiteId, YearMonth)` dedup cache — one fetch per (permit, month) or (permit, division, month)
 * across every request being processed in a tick, not one per request.
 */
@Service
class PermitAvailabilityMatcher(
    private val recreationApi: RecreationApi,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val retryRegistry: RetryRegistry,
    private val zoneAvailabilityBaselineService: ZoneAvailabilityBaselineService,
    @param:Value($$"${campfinder.polling.request-jitter-ms:0}") private val requestJitterMs: Long = 0,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val cb by lazy { circuitBreakerRegistry.circuitBreaker("recreation-gov") }
    private val retry by lazy { retryRegistry.retry("recreation-gov") }

    fun check(
        request: PermitSearchRequest,
        zoneCache: ZoneAvailabilityCache,
        itineraryCache: ItineraryAvailabilityCache,
    ): PermitAvailabilityResult =
        when (request.searchType) {
            SearchType.ZONE -> checkZone(request, zoneCache)
            SearchType.ITINERARY -> checkItinerary(request, itineraryCache)
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
                for (divisionId in target.divisionIds) {
                    val division = payload.availability[divisionId] ?: continue

                    // Always evaluated (and always records this tick as the new baseline) regardless
                    // of the contradiction check below, so the baseline stays fresh every tick even
                    // for divisions skipped via next_available_date.
                    val looksSuspiciousVsBaseline = zoneAvailabilityBaselineService.looksSuspicious(
                        request.permitId,
                        divisionId,
                        month,
                        division.dateAvailability,
                    )

                    val cutoff = payload.nextAvailableDate
                    val isContradictory = cutoff != null &&
                        division.dateAvailability.any { (dateTime, cell) -> dateTime.isBefore(cutoff) && cell.remaining > 0 }

                    if (isContradictory || looksSuspiciousVsBaseline) {
                        log.warn(
                            "Skipping zone permit division with untrustworthy availability permitId={} divisionId={} contradictory={} suspiciousVsBaseline={} nextAvailableDate={}",
                            request.permitId,
                            divisionId,
                            isContradictory,
                            looksSuspiciousVsBaseline,
                            cutoff,
                        )
                        continue
                    }

                    val match = division.dateAvailability.entries
                        .filter { (dateTime, _) ->
                            val date = dateTime.toLocalDate()
                            !date.isBefore(target.startDay) && !date.isAfter(target.endDay)
                        }.sortedBy { it.key }
                        .firstOrNull { it.value.remaining > 0 }
                    if (match != null) {
                        log.info(
                            "Zone permit availability match permitId={} divisionId={} matchedDate={} response={}",
                            request.permitId,
                            divisionId,
                            match.key.toLocalDate(),
                            fetch.rawJson,
                        )
                        return PermitAvailabilityResult(
                            request,
                            hasAvailability = true,
                            matchedDivisionId = divisionId,
                            matchedDate = match.key.toLocalDate(),
                        )
                    }
                }
            }
            monthStart = monthStart.plusMonths(1)
        }
        return PermitAvailabilityResult(request, hasAvailability = false)
    }

    private fun fetchZoneMonth(permitId: String, month: YearMonth, cache: ZoneAvailabilityCache): ZoneMonthFetch =
        cache
            .computeIfAbsent(Pair(permitId, month)) {
                CompletableFuture.supplyAsync { fetchZoneMonthDirect(permitId, month) }
            }.get()

    private fun fetchZoneMonthDirect(permitId: String, month: YearMonth): ZoneMonthFetch {
        sleepJitter(requestJitterMs)
        return try {
            retry.executeSupplier {
                cb.executeSupplier {
                    val startDate = month
                        .atDay(1)
                        .atStartOfDay()
                        .atZone(ZoneOffset.UTC)
                        .format(dateFormatter)
                    val response = recreationApi.getZonePermitAvailability(permitId, startDate).execute()
                    val rawJson = RawResponseCapture.takeAndClear()
                    ZoneMonthFetch(payload = response.body()?.payload, rawJson = rawJson)
                }
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
            // one simultaneously (e.g. a constant per-day quota and a per-member quota).
            val available = quotaMaps.isNotEmpty() && quotaMaps.values.all { byDate -> (byDate[leg.date]?.remaining ?: 0) > 0 }
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
            retry.executeSupplier {
                cb.executeSupplier {
                    recreationApi
                        .getItineraryDivisionAvailability(permitId, divisionId, month.monthValue, month.year)
                        .execute()
                        .body()
                        ?.payload
                }
            }
        } catch (e: Exception) {
            log.warn("Failed to fetch itinerary permit availability permitId={} divisionId={} month={}", permitId, divisionId, month, e)
            null
        }
    }

    companion object {
        private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    }
}
