package com.davismariotti.campalert.provider.camplife

import com.davismariotti.campalert.httpclient.baseProviderObjectMapper
import com.davismariotti.campalert.httpclient.buildBrowserOkHttpClient
import com.davismariotti.campalert.provider.CallProtection
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
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
    /** Extracted from [getCampLifeClient] so tests can verify cookie-jar isolation without a Spring context. Never call this from a shared/singleton context; each caller gets an independent cookie store. */
    internal fun buildOkHttpClient(): OkHttpClient = buildBrowserOkHttpClient(refererOrigin = CAMPLIFE_ORIGIN, metricName = "Custom/CampLife/Request")

    /**
     * CampLife-specific call protection (circuit breaker + retry, no rate limiter), kept entirely
     * independent from [recreationGovCallProtection][com.davismariotti.campalert.provider.recreation.RecreationConfiguration.recreationGovCallProtection]
     * so a CampLife outage or future rate-limiting can never open Recreation.gov's circuit (or vice versa).
     */
    @Bean
    fun campLifeCallProtection(
        circuitBreakerRegistry: CircuitBreakerRegistry,
        retryRegistry: RetryRegistry,
    ): CallProtection =
        CallProtection
            .Builder("camplife")
            .circuitBreaker(circuitBreakerRegistry)
            .retry(retryRegistry)
            .build()

    @Bean
    fun getCampLifeClient(): CampLifeApi {
        val retrofit = Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(buildOkHttpClient())
            .addConverterFactory(JacksonConverterFactory.create(baseProviderObjectMapper()))
            .build()
        return retrofit.create(CampLifeApi::class.java)
    }
}
