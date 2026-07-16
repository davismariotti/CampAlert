package com.davismariotti.campalert.service.turnstile

import com.davismariotti.campalert.httpclient.baseProviderObjectMapper
import okhttp3.OkHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

@Configuration
class TurnstileConfiguration(
    @Value("\${campfinder.turnstile.base-url}") val baseUrl: String,
) {
    @Bean
    fun getTurnstileClient(): TurnstileApi {
        val retrofit = Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(JacksonConverterFactory.create(baseProviderObjectMapper()))
            .build()
        return retrofit.create(TurnstileApi::class.java)
    }
}
