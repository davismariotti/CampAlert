package com.davismariotti.campalert.provider.globalentry

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

private const val GLOBAL_ENTRY_ORIGIN = "https://ttp.dhs.gov"

@Configuration
class GlobalEntryConfiguration(
    @Value("\${globalentry.baseUrl}") val baseUrl: String,
    private val providerHttpClientFactory: ProviderHttpClientFactory,
) {
    /** Extracted from [getGlobalEntryClient] so tests can verify cookie-jar isolation without a Spring context. Never call this from a shared/singleton context; each caller gets an independent cookie store. */
    internal fun buildOkHttpClient(): OkHttpClient =
        providerHttpClientFactory.build(
            provider = Provider.GLOBAL_ENTRY,
            refererOrigin = GLOBAL_ENTRY_ORIGIN,
            metricsInterceptor = MetricsInterceptor.byRetrofitMethod(source = UpstreamSource.provider("GlobalEntry")),
        )

    /** Global Entry's own call protection, independent from every other provider's so one outage can never open another's circuit. */
    @Bean
    fun globalEntryCallProtection(
        circuitBreakerRegistry: CircuitBreakerRegistry,
        retryRegistry: RetryRegistry,
        rateLimiterRegistry: RateLimiterRegistry,
    ): CallProtection =
        CallProtection
            .Builder(Provider.GLOBAL_ENTRY)
            .circuitBreaker(circuitBreakerRegistry)
            .retry(retryRegistry)
            .rateLimiter(rateLimiterRegistry, timeoutEventName = "GlobalEntryRateLimitTimeout")
            .build()

    @Bean
    fun getGlobalEntryClient(): GlobalEntryApi {
        val retrofit = Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(buildOkHttpClient())
            // Built inline, never a @Bean: the app's global ObjectMapper must stay camelCase for the generated API models (CLAUDE.md).
            .addConverterFactory(JacksonConverterFactory.create(baseProviderObjectMapper()))
            .build()
        return retrofit.create(GlobalEntryApi::class.java)
    }
}
