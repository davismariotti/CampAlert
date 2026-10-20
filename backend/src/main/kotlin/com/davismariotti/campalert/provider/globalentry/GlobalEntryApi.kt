package com.davismariotti.campalert.provider.globalentry

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** CBP Trusted Traveler scheduler API (`ttp.cbp.dhs.gov/schedulerapi`) — public, unauthenticated. */
interface GlobalEntryApi {
    @GET("locations/")
    fun getLocations(
        @Query("temporary") temporary: Boolean = false,
        @Query("inviteOnly") inviteOnly: Boolean = false,
        @Query("operational") operational: Boolean = true,
        @Query("serviceName") serviceName: String = SERVICE_NAME,
    ): Call<List<GlobalEntryLocation>>

    /**
     * Every 10-minute slot in `[startTimestamp, endTimestamp)` (`yyyy-MM-ddTHH:mm:ss`, center-local),
     * clamped server-side to the center's last published date. Returned as a raw, streamed body
     * because a multi-week range is ~600KB with thousands of mostly-inactive slots — see
     * [GlobalEntrySlotParser] for why it isn't deserialized into objects.
     */
    @Streaming
    @GET("locations/{id}/slots")
    fun getSlots(
        @Path("id") locationId: Int,
        @Query("startTimestamp") startTimestamp: String,
        @Query("endTimestamp") endTimestamp: String,
    ): Call<ResponseBody>

    companion object {
        const val SERVICE_NAME = "Global Entry"
    }
}
