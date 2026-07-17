package com.davismariotti.campalert.provider.recreation

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

        /** Plain `yyyy-MM-dd`, confirmed live to be what `permitinyo/.../availabilityv2` expects — distinct from [dateFormatter]'s full ISO datetime, which this endpoint does not accept. */
        val trailheadDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
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

    /**
     * Same payload shape as [getPermitContent], but for some permits (confirmed live for Desolation,
     * 233261) `permitcontent/{id}` returns an empty `rules` array while this endpoint has the real
     * rules — [com.davismariotti.campalert.service.permit.PermitContentCache] falls back to this call
     * only when that happens, rather than switching every permit over to it.
     */
    @GET("permits/{id}/details")
    fun getPermitDetails(
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

    /**
     * Single-division view over an arbitrary date range, confirmed live to be computed independently
     * from [getZonePermitAvailability] (same cell shape, but not just a slice of the same payload) —
     * used to corroborate a candidate zone match before trusting a fresh availability transition.
     */
    @GET("permits/{permitId}/divisions/{divisionId}/availability")
    fun getDivisionAvailability(
        @Path("permitId") permitId: String,
        @Path("divisionId") divisionId: String,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("commercial_acct") commercialAcct: Boolean = false,
        @Query("is_lottery") isLottery: Boolean = false,
    ): Call<PermitDivisionAvailabilityResponse>

    /**
     * Every division on a trailhead (`Entry Point`-shaped, [com.davismariotti.campalert.model.SearchType.TRAILHEAD])
     * permit for a date range. Confirmed NOT the same endpoint as [getZonePermitAvailability] — that
     * one returns a hard server error for every `Entry Point`-shaped permit tested, lottery-flagged or
     * not. Requires [startDate]/[endDate] to fall exactly on calendar month boundaries; a partial-month
     * range returns an explicit `"requested dates are invalid"` error, confirmed live.
     */
    @GET("permitinyo/{id}/availabilityv2")
    fun getTrailheadPermitAvailability(
        @Path("id") id: String,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("commercial_acct") commercialAcct: Boolean = false,
    ): Call<PermitTrailheadAvailabilityResponse>

    /**
     * Same endpoint as [getTrailheadPermitAvailability], scoped to one division via an undocumented
     * `division_id` query parameter (confirmed live: same full-month-boundary requirement, values match
     * the unscoped bulk response for the same division/date). Used to corroborate a candidate trailhead
     * match before trusting a fresh availability transition — but unlike [getDivisionAvailability]'s
     * confirmed independence from [getZonePermitAvailability], this call's independence from
     * [getTrailheadPermitAvailability] is NOT confirmed (see design.md decision 7). Used anyway as a
     * knowingly-imperfect safety layer; [PermitAvailabilityMatcher] logs any disagreement to gather
     * evidence on whether it's actually computed independently.
     */
    @GET("permitinyo/{id}/availabilityv2")
    fun getTrailheadDivisionAvailability(
        @Path("id") id: String,
        @Query("division_id") divisionId: String,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("commercial_acct") commercialAcct: Boolean = false,
    ): Call<PermitTrailheadAvailabilityResponse>

    /** Mixed inventory typeahead (rec areas, campgrounds, permits, activity passes) — filter results to `entity_type == "permit"`. */
    @GET("search/suggest")
    fun searchSuggest(
        @Query("q") query: String,
        @Query("geocoder") geocoder: Boolean = true,
    ): Call<SearchSuggestResponse>
}
