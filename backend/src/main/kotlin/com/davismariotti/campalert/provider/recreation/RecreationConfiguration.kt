package com.davismariotti.campalert.provider.recreation

import com.davismariotti.campalert.httpclient.MetricsInterceptor
import com.davismariotti.campalert.httpclient.baseProviderObjectMapper
import com.davismariotti.campalert.httpclient.buildBrowserOkHttpClient
import com.davismariotti.campalert.provider.CallProtection
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import io.github.resilience4j.retry.RetryRegistry
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

    /**
     * Shared "recreation-gov" call protection for every polling call site (campground availability,
     * zone/itinerary permit availability) — rate limiter included, since Recreation.gov's own
     * unversioned/undocumented API is the one provider we've had to explicitly throttle our own
     * offered load against. Not used by permit content/mapping lookups (PermitContentCache,
     * PermitClassificationService) — those are interactive, out of the polling path, and keep using
     * the circuit breaker/retry registries directly.
     */
    @Bean
    fun recreationGovCallProtection(
        circuitBreakerRegistry: CircuitBreakerRegistry,
        retryRegistry: RetryRegistry,
        rateLimiterRegistry: RateLimiterRegistry,
    ): CallProtection =
        CallProtection
            .Builder("recreation-gov")
            .circuitBreaker(circuitBreakerRegistry)
            .retry(retryRegistry)
            .rateLimiter(rateLimiterRegistry, timeoutEventName = "RecreationGovRateLimitTimeout")
            .build()

    /** Extracted from [getRecreationClient] so tests can verify cookie-jar isolation without a Spring context. Never call this from a shared/singleton context; each caller gets an independent cookie store. */
    internal fun buildOkHttpClient(): OkHttpClient =
        buildBrowserOkHttpClient(
            refererOrigin = "https://www.recreation.gov",
            metricName = "Custom/RecreationGov/AvailabilityFetch",
            RawBodyCapturingInterceptor(),
        )

    @Bean
    fun getRecreationClient(): RecreationApi {
        val objectMapper = baseProviderObjectMapper()
            .registerModule(JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
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
            .addConverterFactory(JacksonConverterFactory.create(baseProviderObjectMapper()))
            .build()
        return retrofit.create(RidbApi::class.java)
    }
}
