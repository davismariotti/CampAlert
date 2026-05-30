package com.davismariotti.campfinder.recreation

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
        @Query("start_date") startDate: String = LocalDate.now().atStartOfDay().withDayOfMonth(1).atZone(ZoneOffset.UTC)
            .format(
                dateFormatter
            )
    ): Call<Campground>
}
