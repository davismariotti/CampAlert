package com.davismariotti.campalert.recreation

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface RidbApi {
    @GET("facilities")
    fun getFacilities(
        @Query("query") query: String,
        @Query("limit") limit: Int = 20
    ): Call<RidbFacilitiesResponse>

    @GET("facilities/{id}")
    fun getFacility(
        @Path("id") id: Int
    ): Call<RidbFacilityResponse>

    @GET("facilities/{id}/campsites")
    fun getCampsites(
        @Path("id") id: Int
    ): Call<RidbCampsitesResponse>
}
