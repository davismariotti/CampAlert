package com.davismariotti.campalert.provider.camplife

import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.provider.CallProtection
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.service.availability.AvailabilityResult
import com.davismariotti.campalert.service.availability.CampgroundAvailabilityProvider
import com.davismariotti.campalert.service.availability.CandidateWindows
import com.davismariotti.campalert.service.availability.CheckCycleCache
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture

/**
 * CampLife's [CampgroundAvailabilityProvider] implementation. CampLife's availability endpoint
 * returns `{id, isFiltered}` objects: `isFiltered=true` means the site was excluded by the requested
 * `cgAmenity`/`siteTypeId` filters (verified against real per-site data — see [CampLifeModels]), so
 * amenity AND grouping matching are both resolved server-side via that flag, not locally. Only
 * `site_ids` and group size (via the cached `siteMap`'s `maxOccupants`) are matched locally, since
 * neither has a server-side filter equivalent.
 */
@Service
class CampLifeAvailabilityProvider(
    private val campLifeApi: CampLifeApi,
    @Qualifier("campLifeCallProtection") private val callProtection: CallProtection,
    private val campLifeCatalogCache: CampLifeCatalogCache,
) : CampgroundAvailabilityProvider {
    private val log = LoggerFactory.getLogger(javaClass)

    // Not exposed as a Spring @Bean — used only to parse CampLife's error-response body, mirroring
    // the same (non-bean) inline ObjectMapper pattern CampLifeConfiguration/RecreationConfiguration use.
    private val errorObjectMapper = jacksonObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    override val provider = Provider.CAMPLIFE

    override fun checkAvailability(
        searchRequest: SearchRequest,
        user: User,
        campgroundCache: CheckCycleCache<*, *>?,
    ): AvailabilityResult {
        val campgroundId = searchRequest.campsiteId
        val siteIds = searchRequest.siteIds
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
        val details = searchRequest.campLifeDetails
        // Site-ID scoping is authoritative when present — siteTypeId (grouping) is not separately enforced (design.md decision 4). Amenity filtering is independent of that precedence and always applies when set.
        val siteTypeId = if (siteIds == null) details?.siteTypeId else null
        val amenityIds = details?.amenityIds ?: emptyList()
        val siteMap = campLifeCatalogCache.getCampgroundCatalog(campgroundId)?.siteMap ?: emptyMap()

        val candidates = CandidateWindows.arrivalDates(searchRequest.startDay, searchRequest.nights, searchRequest.searchEndDay)

        // CampLife has no per-day availability calendar, so every candidate needs its own network
        // call — fire them all in parallel rather than short-circuiting (every candidate is already
        // in flight before any result is known, so there's no savings from stopping early). Each call
        // still goes through campLifeCallProtection.execute individually, so pacing beyond the
        // provider's rate limit is handled entirely by CallProtection (see CampLifeConfiguration),
        // not here. Worst case is bounded by campfinder.search.providers.camplife.max-range-width-days
        // (see design.md decision 3/5). When searchEndDay is null, candidates is just [startDay], so
        // this is exactly one call, matching prior behavior.
        val futuresByCandidate = candidates.associateWith { candidateStart ->
            val candidateEnd = candidateStart.plusDays(searchRequest.nights.toLong())
            CompletableFuture.supplyAsync {
                matchedSitesFor(campgroundId, candidateStart, candidateEnd, amenityIds, siteTypeId, siteIds, searchRequest.groupSize, siteMap)
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

    /** Fetches and matches sites for a single candidate stay — one CampLife API call. */
    private fun matchedSitesFor(
        campgroundId: Int,
        checkin: LocalDate,
        checkout: LocalDate,
        amenityIds: List<Int>,
        siteTypeId: Int?,
        siteIds: Set<String>?,
        groupSize: Int,
        siteMap: Map<String, CampLifeSite>,
    ): Set<String> {
        val response = fetchAvailability(campgroundId, checkin, checkout, amenityIds, siteTypeId)
        // isFiltered=true means CampLife excluded the site from the requested cgAmenity/siteTypeId
        // filters — never a match, regardless of what our own catalog says about it.
        val rawSiteIds = response
            ?.sites
            ?.filterNot { it.isFiltered }
            ?.map { it.id.toString() }
            ?.toSet() ?: emptySet()
        if (rawSiteIds.isEmpty()) return emptySet()

        return rawSiteIds
            .filter { id ->
                val site = siteMap[id] ?: return@filter false
                matchesRequest(site, siteIds, groupSize)
            }.toSet()
    }

    private fun matchesRequest(
        site: CampLifeSite,
        siteIds: Set<String>?,
        groupSize: Int,
    ): Boolean {
        if (siteIds != null && site.id.toString() !in siteIds) return false
        // CampLife exposes no per-site minimum-occupancy field — only a maximum.
        val maxOccupancy = site.maxOccupants
        if (maxOccupancy != null && groupSize > maxOccupancy) return false
        return true
    }

    private fun fetchAvailability(
        campgroundId: Int,
        checkin: LocalDate,
        checkout: LocalDate,
        amenityIds: List<Int>,
        siteTypeId: Int?
    ): CampLifeAvailabilityResponse? =
        try {
            callProtection.execute {
                val call = campLifeApi.getAvailability(
                    campgroundId.toString(),
                    CampLifeAvailabilityRequest(
                        checkinDate = checkin.format(dateFormatter),
                        checkoutDate = checkout.format(dateFormatter),
                        cgAmenity = amenityIds,
                        siteTypeId = siteTypeId,
                    ),
                )
                val httpResponse = call.execute()
                if (httpResponse.isSuccessful) {
                    httpResponse.body() ?: CampLifeAvailabilityResponse()
                } else {
                    val parsed = httpResponse.errorBody()?.string()?.let(this::parseErrorBody)
                    val reason = parsed
                        ?.errors
                        ?.general
                        ?.firstOrNull()
                        ?.message ?: parsed
                        ?.warnings
                        ?.general
                        ?.firstOrNull()
                        ?.message
                    if (reason != null) {
                        log.info("CampLife availability rejected campgroundId={} httpStatus={} reason={}", campgroundId, httpResponse.code(), reason)
                    } else {
                        log.warn("CampLife availability call failed campgroundId={} httpStatus={}", campgroundId, httpResponse.code())
                    }
                    parsed ?: CampLifeAvailabilityResponse()
                }
            }
        } catch (e: Exception) {
            log.warn("CampLife availability call failed campgroundId={}", campgroundId, e)
            null
        }

    private fun parseErrorBody(body: String): CampLifeAvailabilityResponse? =
        try {
            errorObjectMapper.readValue(body, CampLifeAvailabilityResponse::class.java)
        } catch (e: Exception) {
            log.warn("Failed to parse CampLife error/warning response body", e)
            null
        }

    companion object {
        val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd-yyyy")
    }
}
