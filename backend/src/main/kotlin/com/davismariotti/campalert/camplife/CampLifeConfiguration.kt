package com.davismariotti.campalert.camplife

import com.davismariotti.campalert.httpclient.BrowserHeadersInterceptor
import com.davismariotti.campalert.httpclient.InMemoryCookieJar
import com.davismariotti.campalert.httpclient.MetricsInterceptor
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.OkHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

private const val CAMPLIFE_ORIGIN = "https://www.camplife.com"

@Configuration
class CampLifeConfiguration(
    @Value("\${camplife.baseUrl}") val baseUrl: String,
) {
    /** Builds a fresh [OkHttpClient] with its own [InMemoryCookieJar] instance — extracted from [getCampLifeClient] so tests can verify cookie-jar isolation without a Spring context. Never call this from a shared/singleton context; each caller gets an independent cookie store. */
    internal fun buildOkHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .cookieJar(InMemoryCookieJar())
            .addInterceptor(BrowserHeadersInterceptor(refererOrigin = CAMPLIFE_ORIGIN))
            .addInterceptor(MetricsInterceptor("Custom/CampLife/Request"))
            .build()

    @Bean
    fun getCampLifeClient(): CampLifeApi {
        val objectMapper = jacksonObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val retrofit = Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(buildOkHttpClient())
            .addConverterFactory(JacksonConverterFactory.create(objectMapper))
            .build()
        return retrofit.create(CampLifeApi::class.java)
    }
}
