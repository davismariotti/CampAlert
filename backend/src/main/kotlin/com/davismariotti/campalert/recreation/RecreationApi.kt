package com.davismariotti.campalert.recreation

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

interface RecreationApi {
    companion object {
        val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    }

    @GET("camps/availability/campground/{id}/month")
    fun getCampgroundAvailability(
        @Path("id") id: Int,
        @Query("start_date") startDate: String = LocalDate
            .now()
            .atStartOfDay()
            .withDayOfMonth(1)
            .atZone(ZoneOffset.UTC)
            .format(
                dateFormatter
            )
    ): Call<Campground>

    /** Global (not permit-specific) classification of every permit id — see decision 1. */
    @GET("permitcontent/permitmapping")
    fun getPermitMapping(): Call<PermitMappingResponse>

    /**
     * Divisions + rules for a single permit id. Confirmed to work for both zone and itinerary
     * permits, so this one call/cache backs classification's structural fallback, `GET /permits/{id}`,
     * and itinerary leg validation (see [com.davismariotti.campalert.service.permit.PermitContentCache]).
     */
    @GET("permitcontent/{id}")
    fun getPermitContent(
        @Path("id") id: String
    ): Call<PermitContentResponse>

    /** One call covers every division on a zone permit for the given month. */
    @GET("permits/{id}/availability/month")
    fun getZonePermitAvailability(
        @Path("id") id: String,
        @Query("start_date") startDate: String,
        @Query("commercial_acct") commercialAcct: Boolean = false,
        @Query("is_lottery") isLottery: Boolean = false,
    ): Call<PermitZoneAvailabilityResponse>

    /** One call per (division, month) on an itinerary permit. */
    @GET("permititinerary/{permitId}/division/{divisionId}/availability/month")
    fun getItineraryDivisionAvailability(
        @Path("permitId") permitId: String,
        @Path("divisionId") divisionId: String,
        @Query("month") month: Int,
        @Query("year") year: Int,
    ): Call<PermitItineraryAvailabilityResponse>

    /** Mixed inventory typeahead (rec areas, campgrounds, permits, activity passes) — filter results to `entity_type == "permit"`. */
    @GET("search/suggest")
    fun searchSuggest(
        @Query("q") query: String,
        @Query("geocoder") geocoder: Boolean = true,
    ): Call<SearchSuggestResponse>
}
