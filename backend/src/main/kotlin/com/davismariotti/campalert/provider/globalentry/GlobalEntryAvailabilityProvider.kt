package com.davismariotti.campalert.provider.globalentry

import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.provider.CallProtection
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.service.availability.AvailabilityResult
import com.davismariotti.campalert.service.availability.CampgroundAvailabilityProvider
import com.davismariotti.campalert.service.availability.CheckCycleCache
import com.davismariotti.campalert.service.availability.GlobalEntryMatch
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

/**
 * Global Entry's [CampgroundAvailabilityProvider]. A request is available when any bookable slot at its
 * enrollment center falls on a requested day and inside its optional time-of-day window (see
 * [GlobalEntrySlotMatcher]). Poll targets are per center, so every request in a check shares a center:
 * one slots call covers the union of their date ranges and is shared across the whole cycle via
 * [SlotCache].
 */
@Service
class GlobalEntryAvailabilityProvider(
    private val globalEntryApi: GlobalEntryApi,
    @Qualifier("globalEntryCallProtection") private val callProtection: CallProtection,
) : CampgroundAvailabilityProvider {
    override val provider = Provider.GLOBAL_ENTRY

    /** Overridable in tests; production always uses the system clock. */
    internal var clock: Clock = Clock.systemUTC()

    /**
     * One fetch per center for a poll cycle, covering `[rangeStart, rangeEndExclusive)`; [compute] runs at most
     * once per center. A failed fetch is memoized too, so every other request at that center in the cycle fails
     * fast with the same error instead of repeating a retried (and breaker-counted) call.
     */
    class SlotCache(
        val rangeStart: LocalDate,
        val rangeEndExclusive: LocalDate,
    ) : CheckCycleCache<Int, List<LocalDateTime>> {
        private val outcomeByCenter = ConcurrentHashMap<Int, Result<List<LocalDateTime>>>()

        override fun computeIfAbsent(key: Int, compute: () -> List<LocalDateTime>): List<LocalDateTime> = outcomeByCenter.computeIfAbsent(key) { runCatching(compute) }.getOrThrow()
    }

    override fun newCheckCycleCache(requests: List<SearchRequest>): CheckCycleCache<*, *>? {
        if (requests.isEmpty()) return null
        val first = requests.minOf { it.startDay }
        val last = requests.maxOf { it.latestStartDay ?: it.startDay }
        return SlotCache(notBeforeToday(first), last.plusDays(1))
    }

    override fun checkAvailability(
        searchRequest: SearchRequest,
        user: User,
        campgroundCache: CheckCycleCache<*, *>?,
    ): AvailabilityResult {
        val firstDay = searchRequest.startDay
        val lastDay = searchRequest.latestStartDay ?: searchRequest.startDay
        val centerId = searchRequest.campsiteId

        val cache = campgroundCache as? SlotCache
        val slots = if (cache != null) {
            cache.computeIfAbsent(centerId) { fetchBookableSlots(centerId, cache.rangeStart, cache.rangeEndExclusive) }
        } else {
            fetchBookableSlots(centerId, notBeforeToday(firstDay), lastDay.plusDays(1))
        }

        val details = searchRequest.globalEntryDetails
        val zone = searchRequest.campgroundTimezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.UTC
        val match = GlobalEntrySlotMatcher.match(
            bookableSlots = slots,
            firstDay = firstDay,
            lastDay = lastDay,
            earliestTime = details?.earliestTime,
            latestTime = details?.latestTime,
            now = LocalDateTime.now(clock.withZone(zone)),
        ) ?: return AvailabilityResult(searchRequest = searchRequest, hasAvailableSites = false, availableSiteCount = 0)

        return AvailabilityResult(
            searchRequest = searchRequest,
            hasAvailableSites = true,
            availableSiteCount = match.count,
            matchedStartDay = match.earliest.toLocalDate(),
            matchedEndDay = match.earliest.toLocalDate(),
            globalEntryMatch = GlobalEntryMatch(earliestStartTime = match.earliest.toLocalTime(), slotCount = match.count),
        )
    }

    /**
     * A flexible request's start day can already be past while its range is still open; those days' slots can never
     * match, so don't re-download them every cycle. Clamped to yesterday in UTC, which is never later than "today" in
     * any center's zone (UTC-12 is the earliest), so no still-matchable day is ever dropped.
     */
    private fun notBeforeToday(day: LocalDate): LocalDate {
        val safeFloor = LocalDate.now(clock.withZone(ZoneOffset.UTC)).minusDays(1)
        return if (day.isBefore(safeFloor)) safeFloor else day
    }

    private fun fetchBookableSlots(centerId: Int, rangeStart: LocalDate, rangeEndExclusive: LocalDate): List<LocalDateTime> =
        callProtection.execute {
            val response = globalEntryApi.getSlots(centerId, "${rangeStart}T00:00:00", "${rangeEndExclusive}T00:00:00").execute()
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                response.errorBody()?.close()
                throw IOException("Global Entry slots request failed centerId=$centerId status=${response.code()}")
            }
            body.use { GlobalEntrySlotParser.parseBookable(it.byteStream()) }
        }
}
