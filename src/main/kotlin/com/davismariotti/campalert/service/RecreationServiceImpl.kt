package com.davismariotti.campalert.service

import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.recreation.AvailabilityType
import com.davismariotti.campalert.recreation.Campground
import com.davismariotti.campalert.recreation.Campsite.Companion.mergeWith
import com.davismariotti.campalert.recreation.RecreationApi
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Service
class RecreationServiceImpl(
    val recreationApi: RecreationApi,
    val pushoverService: PushoverService
) : RecreationService {
    override fun checkAvailability(searchRequest: SearchRequest) {
        // Find months
        val endNight = searchRequest.startDay.plusDays(searchRequest.nights.toLong())
        var monthStart = searchRequest.startDay.withDayOfMonth(1)
        var campground: Campground = recreationApi
            .getCampgroundAvailability(
                searchRequest.campsiteId,
                monthStart.atStartOfDay().atZone(ZoneOffset.UTC).format(
                    dateFormatter
                )
            ).execute()
            .body()!!

        while (monthStart.isBeforeMonth(endNight.plusMonths(1))) {
            recreationApi
                .getCampgroundAvailability(
                    searchRequest.campsiteId,
                    monthStart.atStartOfDay().atZone(ZoneOffset.UTC).format(
                        dateFormatter
                    )
                ).execute()
                .body()
                ?.let { fetchedCampground ->
                    campground = campground.mergeWith(fetchedCampground)
                }
            monthStart = monthStart.plusMonths(1)
        }

        campground.filterByLoops(searchRequest.loops)
        campground.filterByGroupSize(searchRequest.groupSize)
        campground.removeExtraDates(searchRequest.startDay, endNight)
        campground.filterByAvailability()

        pushoverService.pushMessage(searchRequest, campground)
    }

    private fun Campground.filterByLoops(loops: List<String>?) {
        loops?.let { validLoops ->
            val lowerCaseValidLoops = validLoops.map { it.lowercase() }
            campsites = campsites.filterValues { it.loop.lowercase() in lowerCaseValidLoops }
        }
    }

    private fun Campground.filterByGroupSize(groupSize: Int) {
        campsites =
            campsites.filterValues { it.minimumNumberOfPeople <= groupSize && it.maximumNumberOfPeople >= groupSize }
    }

    private fun Campground.removeExtraDates(startDay: LocalDate, endDay: LocalDate) {
        campsites.forEach {
            it.value.run {
                availabilities = availabilities.filterKeys { key -> key.isBetween(startDay, endDay) }
                quantities = quantities.filterKeys { key -> key.isBetween(startDay, endDay) }
            }
        }
    }

    private fun Campground.filterByAvailability() {
        campsites =
            campsites.filterValues {
                it.availabilities.values.all { availabilityType ->
                    availabilityType == AvailabilityType.AVAILABLE
                }
            }
    }

    companion object {
        val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    }

    fun LocalDate.isBeforeMonth(other: LocalDate): Boolean =
        this.year < other.year || (this.year == other.year && this.monthValue < other.monthValue)

    fun ZonedDateTime.isBetween(startDate: LocalDate, endDate: LocalDate): Boolean {
        val startOfDay = startDate.atStartOfDay(this.zone)
        val endOfDay = endDate.atStartOfDay(this.zone)
        return this.isAfter(startOfDay.minusNanos(1)) && this.isBefore(endOfDay)
    }
}
