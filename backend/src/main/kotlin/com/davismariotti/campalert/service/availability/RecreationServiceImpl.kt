package com.davismariotti.campalert.service.availability

import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.recreation.AvailabilityType
import com.davismariotti.campalert.recreation.Campground
import com.davismariotti.campalert.recreation.Campsite.Companion.mergeWith
import com.davismariotti.campalert.recreation.RecreationApi
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
) : RecreationService {
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
                campgroundCache.computeIfAbsent(Pair(searchRequest.campsiteId, month)) {
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

        campground = campground ?: Campground(emptyMap())
        campground.filterByLoops(searchRequest.loops)
        campground.filterByGroupSize(searchRequest.groupSize)
        campground.removeExtraDates(searchRequest.startDay, endNight)
        campground.filterByAvailability()

        return AvailabilityResult(
            searchRequest = searchRequest,
            campground = campground,
            hasAvailableSites = campground.campsites.isNotEmpty(),
        )
    }

    private fun fetchMonth(campsiteId: Int, monthStart: LocalDate): Campground =
        recreationApi
            .getCampgroundAvailability(
                campsiteId,
                monthStart.atStartOfDay().atZone(ZoneOffset.UTC).format(dateFormatter),
            ).execute()
            .body()!!

    private fun Campground.filterByLoops(loops: List<String>?) {
        loops?.let { validLoops ->
            val lower = validLoops.map { it.lowercase() }
            campsites = campsites.filterValues { it.loop.lowercase() in lower }
        }
    }

    private fun Campground.filterByGroupSize(groupSize: Int) {
        campsites = campsites.filterValues {
            it.minimumNumberOfPeople <= groupSize && it.maximumNumberOfPeople >= groupSize
        }
    }

    private fun Campground.removeExtraDates(startDay: LocalDate, endDay: LocalDate) {
        campsites.forEach { (_, site) ->
            site.availabilities = site.availabilities.filterKeys { it.isBetween(startDay, endDay) }
            site.quantities = site.quantities.filterKeys { it.isBetween(startDay, endDay) }
        }
    }

    private fun Campground.filterByAvailability() {
        campsites = campsites.filterValues {
            it.availabilities.values.all { type -> type == AvailabilityType.AVAILABLE }
        }
    }

    companion object {
        val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    }

    private fun LocalDate.isBeforeMonth(other: LocalDate): Boolean =
        this.year < other.year || (this.year == other.year && this.monthValue < other.monthValue)

    private fun ZonedDateTime.isBetween(startDate: LocalDate, endDate: LocalDate): Boolean {
        val startOfDay = startDate.atStartOfDay(this.zone)
        val endOfDay = endDate.atStartOfDay(this.zone)
        return this.isAfter(startOfDay.minusNanos(1)) && this.isBefore(endOfDay)
    }
}
