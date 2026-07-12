package com.davismariotti.campalert.service.availability

import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.recreation.AvailabilityType
import com.davismariotti.campalert.recreation.Campground
import com.davismariotti.campalert.recreation.Campsite
import com.davismariotti.campalert.recreation.Campsite.Companion.mergeWith
import com.davismariotti.campalert.recreation.RecreationApi
import com.davismariotti.campalert.recreation.RecreationGovCallProtection
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
) : RecreationService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun checkAvailability(
        searchRequest: SearchRequest,
        user: User,
        campgroundCache: ConcurrentHashMap<Pair<Int, YearMonth>, CompletableFuture<Campground>>?,
    ): AvailabilityResult {
        val endNight = searchRequest.startDay.plusDays(searchRequest.nights.toLong())
        var monthStart = searchRequest.startDay.withDayOfMonth(1)
        var campground: Campground? = null

        while (monthStart.isBeforeMonth(endNight.plusMonths(1))) {
            val month = YearMonth.from(monthStart)
            val capturedMonthStart = monthStart

            val monthly = if (campgroundCache != null) {
                campgroundCache
                    .computeIfAbsent(Pair(searchRequest.campsiteId, month)) {
                        CompletableFuture.supplyAsync {
                            fetchMonth(searchRequest.campsiteId, capturedMonthStart)
                        }
                    }.get()
            } else {
                fetchMonth(searchRequest.campsiteId, capturedMonthStart)
            }

            campground = if (campground == null) monthly else campground.mergeWith(monthly)
            monthStart = monthStart.plusMonths(1)
        }

        val loops = searchRequest.loops?.map { it.lowercase() }
        val availableSites = (campground ?: Campground(emptyMap()))
            .campsites
            .filterValues { site ->
                matchesRequest(site, loops, searchRequest.groupSize, searchRequest.startDay, endNight)
            }

        return AvailabilityResult(
            searchRequest = searchRequest,
            campground = Campground(availableSites),
            hasAvailableSites = availableSites.isNotEmpty(),
        )
    }

    private fun matchesRequest(
        site: Campsite,
        loops: List<String>?,
        groupSize: Int,
        startDay: LocalDate,
        endNight: LocalDate,
    ): Boolean {
        if (loops != null && site.loop.lowercase() !in loops) return false
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
