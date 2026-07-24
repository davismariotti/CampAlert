package com.davismariotti.campalert.provider.reservecalifornia

import com.davismariotti.campalert.model.ReserveCaliforniaSearchRequestDetails
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.provider.CallProtection
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.service.availability.AvailabilityResult
import com.davismariotti.campalert.service.availability.CampgroundAvailabilityProvider
import com.davismariotti.campalert.service.availability.CandidateWindows
import com.davismariotti.campalert.service.availability.CheckCycleCache
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletableFuture

/**
 * ReserveCalifornia's [CampgroundAvailabilityProvider] implementation (design.md D7-D10). ReserveCalifornia's
 * grid endpoint returns `IsFiltered` per unit — the server-side resolution of any unitCategoryId/
 * sleepingUnitId/minVehicleLength/amenityIds filters sent in the request, verified to never omit a
 * unit for failing a filter or being fully unavailable (see the `reserve-california-availability-provider`
 * spec). Grouping/amenity matching is therefore resolved entirely via `IsFiltered`, never re-derived
 * locally — the same principle CampLifeAvailabilityProvider already follows for CampLife's `isFiltered`.
 *
 * Occupancy (group-size) matching is the one piece with no CampLife/Recreation.gov analogue: the grid
 * response carries no occupancy field at all, so any-site (`site_ids == null`) searches with
 * `groupSize > 1` can only match units whose occupancy has already been fetched by the warm-up
 * pipeline (`ReserveCaliforniaOccupancyService`) — unknown occupancy is never assumed to fit (D10).
 * `site_ids`-scoped searches instead resolve occupancy lazily for just their own specific units and
 * never wait on or trigger the facility-wide warm-up (D17).
 */
@Service
class ReserveCaliforniaAvailabilityProvider(
    private val reserveCaliforniaApi: ReserveCaliforniaApi,
    @Qualifier("reserveCaliforniaCallProtection") private val callProtection: CallProtection,
    private val occupancyService: ReserveCaliforniaOccupancyService,
) : CampgroundAvailabilityProvider {
    private val log = LoggerFactory.getLogger(javaClass)

    override val provider = Provider.RESERVE_CALIFORNIA

    override fun checkAvailability(
        searchRequest: SearchRequest,
        user: User,
        campgroundCache: CheckCycleCache<*, *>?,
    ): AvailabilityResult {
        val facilityId = searchRequest.campsiteId
        val siteIds = searchRequest.siteIds
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
        val details = searchRequest.reserveCaliforniaDetails

        val candidates = CandidateWindows.arrivalDates(searchRequest.startDay, searchRequest.latestStartDay)

        // Every candidate window needs its own grid call (no shared per-day availability calendar
        // across candidates) — fired in parallel, same pattern CampLifeAvailabilityProvider uses.
        val futuresByCandidate = candidates.associateWith { candidateStart ->
            val candidateEnd = candidateStart.plusDays(searchRequest.nights.toLong())
            CompletableFuture.supplyAsync {
                matchedSitesFor(facilityId, candidateStart, candidateEnd, details, siteIds, searchRequest.groupSize)
            }
        }

        for (candidateStart in candidates) {
            val matched = futuresByCandidate.getValue(candidateStart).get()
            if (matched.isNotEmpty()) {
                return AvailabilityResult(
                    searchRequest = searchRequest,
                    hasAvailableSites = true,
                    availableSiteCount = matched.size,
                    availableSiteIds = matched,
                    matchedStartDay = candidateStart,
                    matchedEndDay = candidateStart.plusDays(searchRequest.nights.toLong()),
                )
            }
        }

        return AvailabilityResult(searchRequest = searchRequest, hasAvailableSites = false, availableSiteCount = 0)
    }

    /** Fetches and matches sites for a single candidate stay — one ReserveCalifornia grid call. */
    private fun matchedSitesFor(
        facilityId: Int,
        checkin: LocalDate,
        checkout: LocalDate,
        details: ReserveCaliforniaSearchRequestDetails?,
        siteIds: Set<String>?,
        groupSize: Int,
    ): Set<String> {
        val response = fetchGrid(facilityId, checkin, checkout, details) ?: return emptySet()
        val facility = response.facility ?: return emptySet()
        val nightCount = ChronoUnit.DAYS.between(checkin, checkout).toInt()
        val nightKeys = generateSequence(checkin) { it.plusDays(1) }
            .take(nightCount)
            .map { "${it.format(DATE_FORMATTER)}T00:00:00" }
            .toList()

        // IsFiltered resolves category/amenity/vehicle-length matching server-side (D8) — never
        // re-derived here. A candidate site must also be free for every night of the stay.
        val rawMatches = facility.units.values
            .asSequence()
            .filterNot { it.isFiltered }
            .filter { unit -> nightKeys.all { key -> unit.slices[key]?.isFree == true } }
            .filter { unit -> siteIds == null || unit.unitId.toString() in siteIds }
            .map { it.unitId }
            .toSet()
        if (rawMatches.isEmpty()) return emptySet()

        val occupancyEligible = when {
            siteIds != null -> occupancyService.resolveSufficientForSiteIds(facilityId, rawMatches, groupSize)
            groupSize <= 1 -> rawMatches
            else -> occupancyService.findFetchedSufficientFor(facilityId, rawMatches, groupSize)
        }
        return occupancyEligible.map { it.toString() }.toSet()
    }

    private fun fetchGrid(
        facilityId: Int,
        checkin: LocalDate,
        checkout: LocalDate,
        details: ReserveCaliforniaSearchRequestDetails?,
    ): ReserveCaliforniaGridResponse? =
        try {
            callProtection.execute {
                val body = ReserveCaliforniaGridRequest(
                    facilityId = facilityId.toString(),
                    startDate = checkin.format(DATE_FORMATTER),
                    endDate = checkout.format(DATE_FORMATTER),
                    unitCategoryId = details?.unitCategoryId ?: 0,
                    unitTypesGroupIds = details?.unitTypeGroupIds ?: emptyList(),
                    sleepingUnitId = details?.sleepingUnitId ?: 0,
                    minVehicleLength = details?.minVehicleLength ?: 0,
                    amenityIds = details?.amenityIds ?: emptyList(),
                    minDate = "${LocalDate.now().format(DATE_FORMATTER)}T00:00:00",
                    maxDate = "${LocalDate.now().plusDays(180).format(DATE_FORMATTER)}T00:00:00",
                )
                reserveCaliforniaApi.getGrid(body).execute().body()
            }
        } catch (e: Exception) {
            log.warn("ReserveCalifornia availability call failed facilityId={}", facilityId, e)
            null
        }

    companion object {
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
