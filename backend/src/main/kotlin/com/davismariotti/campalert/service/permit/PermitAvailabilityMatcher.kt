package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.model.PermitSearchRequest
import com.davismariotti.campalert.model.SearchType
import com.davismariotti.campalert.recreation.PermitItineraryAvailabilityPayload
import com.davismariotti.campalert.recreation.PermitZoneAvailabilityPayload
import com.davismariotti.campalert.recreation.RecreationApi
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

typealias ZoneAvailabilityCache = ConcurrentHashMap<Pair<String, YearMonth>, CompletableFuture<PermitZoneAvailabilityPayload?>>
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
            val payload = fetchZoneMonth(request.permitId, month, cache)
            if (payload != null) {
                for (divisionId in target.divisionIds) {
                    val division = payload.availability[divisionId] ?: continue
                    val match = division.dateAvailability.entries
                        .filter { (dateTime, _) ->
                            val date = dateTime.toLocalDate()
                            !date.isBefore(target.startDay) && !date.isAfter(target.endDay)
                        }.sortedBy { it.key }
                        .firstOrNull { it.value.remaining > 0 }
                    if (match != null) {
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

    private fun fetchZoneMonth(permitId: String, month: YearMonth, cache: ZoneAvailabilityCache): PermitZoneAvailabilityPayload? =
        cache
            .computeIfAbsent(Pair(permitId, month)) {
                CompletableFuture.supplyAsync { fetchZoneMonthDirect(permitId, month) }
            }.get()

    private fun fetchZoneMonthDirect(permitId: String, month: YearMonth): PermitZoneAvailabilityPayload? =
        try {
            retry.executeSupplier {
                cb.executeSupplier {
                    val startDate = month
                        .atDay(1)
                        .atStartOfDay()
                        .atZone(ZoneOffset.UTC)
                        .format(dateFormatter)
                    recreationApi
                        .getZonePermitAvailability(permitId, startDate)
                        .execute()
                        .body()
                        ?.payload
                }
            }
        } catch (e: Exception) {
            log.warn("Failed to fetch zone permit availability permitId={} month={}", permitId, month, e)
            null
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

    private fun fetchItineraryMonthDirect(permitId: String, divisionId: String, month: YearMonth): PermitItineraryAvailabilityPayload? =
        try {
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

    companion object {
        private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    }
}
