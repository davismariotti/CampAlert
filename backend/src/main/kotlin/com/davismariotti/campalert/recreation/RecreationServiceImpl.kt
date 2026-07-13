package com.davismariotti.campalert.recreation

import com.davismariotti.campalert.model.Provider
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.recreation.Campsite.Companion.mergeWith
import com.davismariotti.campalert.service.availability.AvailabilityResult
import com.davismariotti.campalert.service.availability.CampgroundAvailabilityProvider
import com.davismariotti.campalert.service.availability.CheckCycleCache
import com.davismariotti.campalert.util.sleepJitter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Service
class RecreationServiceImpl(
    val recreationApi: RecreationApi,
    private val callProtection: RecreationGovCallProtection,
    @param:Value($$"${campfinder.polling.request-jitter-ms:0}") private val requestJitterMs: Long = 0,
) : CampgroundAvailabilityProvider {
    private val log = LoggerFactory.getLogger(javaClass)

    override val provider = Provider.RECREATION_GOV

    /** Month-chunked dedup cache: one fetch per (campgroundId, month) across every request processed in a cycle, not one per request — [compute] runs on a background thread so distinct-month fetches within a cycle still happen concurrently. */
    class MonthFetchCache : CheckCycleCache<Pair<Int, YearMonth>, Campground> {
        private val futures = ConcurrentHashMap<Pair<Int, YearMonth>, CompletableFuture<Campground>>()

        override fun computeIfAbsent(key: Pair<Int, YearMonth>, compute: () -> Campground): Campground = futures.computeIfAbsent(key) { CompletableFuture.supplyAsync(compute) }.get()
    }

    override fun newCheckCycleCache(): CheckCycleCache<*, *> = MonthFetchCache()

    override fun checkAvailability(
        searchRequest: SearchRequest,
        user: User,
        campgroundCache: CheckCycleCache<*, *>?,
    ): AvailabilityResult {
        @Suppress("UNCHECKED_CAST")
        val cache = campgroundCache as? CheckCycleCache<Pair<Int, YearMonth>, Campground>
        val endNight = searchRequest.startDay.plusDays(searchRequest.nights.toLong())
        var monthStart = searchRequest.startDay.withDayOfMonth(1)
        var campground: Campground? = null

        while (monthStart.isBeforeMonth(endNight.plusMonths(1))) {
            val month = YearMonth.from(monthStart)
            val capturedMonthStart = monthStart

            val monthly = if (cache != null) {
                cache.computeIfAbsent(Pair(searchRequest.campsiteId, month)) {
                    fetchMonth(searchRequest.campsiteId, capturedMonthStart)
                }
            } else {
                fetchMonth(searchRequest.campsiteId, capturedMonthStart)
            }

            campground = if (campground == null) monthly else campground.mergeWith(monthly)
            monthStart = monthStart.plusMonths(1)
        }

        val siteIds = searchRequest.siteIds
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
        // Site-ID scoping is authoritative when present — loops is not separately enforced (design.md decision 4).
        val loops = if (siteIds == null) searchRequest.recreationGovDetails?.loops?.map { it.lowercase() } else null
        val availableSites = (campground ?: Campground(emptyMap()))
            .campsites
            .filterValues { site ->
                matchesRequest(site, loops, siteIds, searchRequest.groupSize, searchRequest.startDay, endNight)
            }

        return AvailabilityResult(
            searchRequest = searchRequest,
            hasAvailableSites = availableSites.isNotEmpty(),
            availableSiteCount = availableSites.size,
            availableSiteIds = availableSites.keys.map { it.toString() }.toSet(),
        )
    }

    private fun matchesRequest(
        site: Campsite,
        loops: List<String>?,
        siteIds: Set<String>?,
        groupSize: Int,
        startDay: LocalDate,
        endNight: LocalDate,
    ): Boolean {
        if (siteIds != null) {
            if (site.campsiteId.toString() !in siteIds) return false
        } else if (loops != null && site.loop.lowercase() !in loops) {
            return false
        }
        if (site.minimumNumberOfPeople > groupSize || site.maximumNumberOfPeople < groupSize) return false
        val relevant = site.availabilities.filterKeys { it.isBetween(startDay, endNight) }
        return relevant.isNotEmpty() && relevant.values.all { it == AvailabilityType.AVAILABLE }
    }

    private fun fetchMonth(campsiteId: Int, monthStart: LocalDate): Campground =
        try {
            callProtection.execute {
                fetchMonthDirect(campsiteId, monthStart)
            }
        } catch (e: Exception) {
            log.warn("Recreation.gov call failed for campsiteId={} month={}", campsiteId, monthStart, e)
            Campground(emptyMap())
        }

    private fun fetchMonthDirect(campsiteId: Int, monthStart: LocalDate): Campground {
        sleepJitter(requestJitterMs)
        val body = recreationApi
            .getCampgroundAvailability(
                campsiteId,
                monthStart.atStartOfDay().atZone(ZoneOffset.UTC).format(dateFormatter),
            ).execute()
            .body()
        if (body == null) {
            log.warn("Null body from Recreation.gov for campsiteId={} month={}", campsiteId, monthStart)
            return Campground(emptyMap())
        }
        return body
    }

    companion object {
        val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    }

    private fun LocalDate.isBeforeMonth(other: LocalDate): Boolean = this.year < other.year || (this.year == other.year && this.monthValue < other.monthValue)

    private fun ZonedDateTime.isBetween(startDate: LocalDate, endDate: LocalDate): Boolean {
        val startOfDay = startDate.atStartOfDay(this.zone)
        val endOfDay = endDate.atStartOfDay(this.zone)
        return this.isAfter(startOfDay.minusNanos(1)) && this.isBefore(endOfDay)
    }
}
