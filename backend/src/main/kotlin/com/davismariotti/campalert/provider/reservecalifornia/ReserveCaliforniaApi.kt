package com.davismariotti.campalert.provider.reservecalifornia

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * `GET rdr/fd/citypark/namecontains/{query}` is deliberately not modeled here — verified (design.md
 * D2/Appendix §1) to match only park/city names, never facility names, so catalog search uses the
 * cached Places+Facilities directory instead of this live endpoint.
 */
interface ReserveCaliforniaApi {
    @GET("rdr/fd/places")
    fun getPlaces(): Call<List<ReserveCaliforniaPlace>>

    @GET("rdr/fd/facilities")
    fun getFacilities(): Call<List<ReserveCaliforniaFacility>>

    @GET("rdr/search/filters/{placeId}")
    fun getFilters(
        @Path("placeId") placeId: Int
    ): Call<ReserveCaliforniaFiltersResponse>

    @POST("rdr/search/grid")
    fun getGrid(
        @Body body: ReserveCaliforniaGridRequest
    ): Call<ReserveCaliforniaGridResponse>

    @GET("rdr/search/details/{unitId}/startdate/{startDate}/nights/{nights}/0/0")
    fun getUnitDetails(
        @Path("unitId") unitId: Int,
        @Path("startDate") startDate: String,
        @Path("nights") nights: Int,
    ): Call<ReserveCaliforniaDetailsResponse>
}
