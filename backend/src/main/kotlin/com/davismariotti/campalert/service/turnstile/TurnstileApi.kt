package com.davismariotti.campalert.service.turnstile

import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface TurnstileApi {
    @FormUrlEncoded
    @POST("turnstile/v0/siteverify")
    fun siteverify(
        @Field("secret") secret: String,
        @Field("response") response: String,
    ): Call<SiteverifyResponse>
}
