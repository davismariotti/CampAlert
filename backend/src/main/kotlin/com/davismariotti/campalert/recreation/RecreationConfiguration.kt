package com.davismariotti.campalert.recreation

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
    fun timeZoneEngine(): TimeZoneEngine = TimeZoneEngine.initialize()

    @Bean
    fun getRecreationClient(): RecreationApi {
        val objectMapper = jacksonObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .registerModule(JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val retrofit = Retrofit
            .Builder()
            .baseUrl(baseUrl)
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
            }.build()
        val retrofit = Retrofit
            .Builder()
            .baseUrl(ridbBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(JacksonConverterFactory.create(objectMapper))
            .build()
        return retrofit.create(RidbApi::class.java)
    }
}
