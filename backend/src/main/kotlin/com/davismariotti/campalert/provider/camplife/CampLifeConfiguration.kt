package com.davismariotti.campalert.provider.camplife

import com.davismariotti.campalert.httpclient.MetricsInterceptor
import com.davismariotti.campalert.httpclient.ProviderHttpClientFactory
import com.davismariotti.campalert.httpclient.UpstreamSource
import com.davismariotti.campalert.httpclient.baseProviderObjectMapper
import com.davismariotti.campalert.provider.CallProtection
import com.davismariotti.campalert.provider.Provider
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
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
    private val providerHttpClientFactory: ProviderHttpClientFactory,
) {
    /** Extracted from [getCampLifeClient] so tests can verify cookie-jar isolation without a Spring context. Never call this from a shared/singleton context; each caller gets an independent cookie store. */
    internal fun buildOkHttpClient(): OkHttpClient =
        providerHttpClientFactory.build(
            provider = Provider.CAMPLIFE,
            refererOrigin = CAMPLIFE_ORIGIN,
            metricsInterceptor = MetricsInterceptor.byRetrofitMethod(source = UpstreamSource.provider("CampLife")),
        )

    /**
     * CampLife-specific call protection (circuit breaker + retry + rate limiter), kept entirely
     * independent from [recreationGovCallProtection][com.davismariotti.campalert.provider.recreation.RecreationConfiguration.recreationGovCallProtection]
     * so a CampLife outage can never open Recreation.gov's circuit (or vice versa). The rate limiter
     * was added alongside flexible-window search: a flexible CampLife check now fires one call per
     * candidate window in parallel (see [CampLifeAvailabilityProvider]), so CampLife's own API needs
     * the same self-imposed ceiling Recreation.gov already has.
     */
    @Bean
    fun campLifeCallProtection(
        circuitBreakerRegistry: CircuitBreakerRegistry,
        retryRegistry: RetryRegistry,
        rateLimiterRegistry: RateLimiterRegistry,
    ): CallProtection =
        CallProtection
            .Builder(Provider.CAMPLIFE)
            .circuitBreaker(circuitBreakerRegistry)
            .retry(retryRegistry)
            .rateLimiter(rateLimiterRegistry, timeoutEventName = "CampLifeRateLimitTimeout")
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
