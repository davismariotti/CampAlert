package com.davismariotti.campalert.provider.recreation

import com.davismariotti.campalert.httpclient.BrowserHeadersInterceptor
import com.davismariotti.campalert.httpclient.InMemoryCookieJar
import com.davismariotti.campalert.httpclient.MetricsInterceptor
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.iakovlev.timeshape.TimeZoneEngine
import okhttp3.OkHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

@Configuration
class RecreationConfiguration(
    @Value("\${recreation.baseUrl}") val baseUrl: String,
    @Value("\${ridb.baseUrl}") val ridbBaseUrl: String,
    @Value("\${ridb.apiKey}") val ridbApiKey: String
) {
    @Bean
    fun timeZoneEngine(): TimeZoneEngine = TimeZoneEngine.initialize(17.0, -180.0, 72.0, -65.0, true)

    /** Builds a fresh [OkHttpClient] with its own [InMemoryCookieJar] instance — extracted from [getRecreationClient] so tests can verify cookie-jar isolation without a Spring context. Never call this from a shared/singleton context; each caller gets an independent cookie store. */
    internal fun buildOkHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .cookieJar(InMemoryCookieJar())
            .addInterceptor(BrowserHeadersInterceptor(refererOrigin = "https://www.recreation.gov"))
            .addInterceptor(MetricsInterceptor("Custom/RecreationGov/AvailabilityFetch"))
            .addInterceptor(RawBodyCapturingInterceptor())
            .build()

    @Bean
    fun getRecreationClient(): RecreationApi {
        val objectMapper = jacksonObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .registerModule(JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            // Lets @JsonEnumDefaultValue-annotated enums (e.g. PermitQuotaType) fall back gracefully
            // instead of throwing when these undocumented, unversioned endpoints send an unrecognized value.
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true)
        val retrofit = Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(buildOkHttpClient())
            .addConverterFactory(JacksonConverterFactory.create(objectMapper))
            .build()
        return retrofit.create(RecreationApi::class.java)
    }

    @Bean
    fun getRidbClient(): RidbApi {
        val objectMapper = jacksonObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val okHttpClient = OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                val request = chain
                    .request()
                    .newBuilder()
                    .addHeader("apikey", ridbApiKey)
                    .build()
                chain.proceed(request)
            }.addInterceptor(MetricsInterceptor("Custom/Ridb/Request"))
            .build()
        val retrofit = Retrofit
            .Builder()
            .baseUrl(ridbBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(JacksonConverterFactory.create(objectMapper))
            .build()
        return retrofit.create(RidbApi::class.java)
    }
}
