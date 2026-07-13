package com.davismariotti.campalert.camplife

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CampLifeApi {
    @GET("campgrounds")
    fun getDirectory(
        @Query("all") all: Boolean = true,
        @Query("minimal") minimal: Boolean = true,
    ): Call<List<CampLifeDirectoryEntry>>

    @GET("reservation/session")
    fun getCampgroundSession(
        @Query("campgroundIdOrAlias") campgroundIdOrAlias: String,
    ): Call<CampLifeSessionResponse>

    @POST("campground/{id}/availability")
    fun getAvailability(
        @Path("id") campgroundId: String,
        @Body body: CampLifeAvailabilityRequest,
    ): Call<CampLifeAvailabilityResponse>
}
