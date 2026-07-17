package com.davismariotti.campalert.service.turnstile

import com.davismariotti.campalert.httpclient.MetricsInterceptor
import com.davismariotti.campalert.httpclient.ProviderHttpClientFactory
import com.davismariotti.campalert.httpclient.baseProviderObjectMapper
import com.davismariotti.campalert.provider.CallProtection
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

private const val TURNSTILE_VENDOR = "turnstile"

@Configuration
class TurnstileConfiguration(
    @Value("\${campfinder.turnstile.base-url}") val baseUrl: String,
    private val providerHttpClientFactory: ProviderHttpClientFactory,
) {
    /**
     * Circuit breaker + retry only, no rate limiter — Turnstile is called once per interactive user
     * action (signup, search request creation), never polled, so there's no self-imposed burst to
     * guard against the way there is for Recreation.gov/CampLife.
     */
    @Bean
    fun turnstileCallProtection(
        circuitBreakerRegistry: CircuitBreakerRegistry,
        retryRegistry: RetryRegistry,
    ): CallProtection =
        CallProtection
            .Builder(TURNSTILE_VENDOR)
            .circuitBreaker(circuitBreakerRegistry)
            .retry(retryRegistry)
            .build()

    @Bean
    fun getTurnstileClient(): TurnstileApi {
        // Not routed through ProviderHttpClientFactory.build() — that method's InMemoryCookieJar and
        // BrowserHeadersInterceptor exist to mimic a browser against scrape targets (Recreation.gov,
        // CampLife); Turnstile is a legitimate first-party call to Cloudflare's own API, so those don't
        // apply here.
        val okHttpClient = providerHttpClientFactory.buildSimple(
            vendor = TURNSTILE_VENDOR,
            metricsInterceptor = MetricsInterceptor("Custom/Turnstile/Verify"),
        )
        val retrofit = Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(JacksonConverterFactory.create(baseProviderObjectMapper()))
            .build()
        return retrofit.create(TurnstileApi::class.java)
    }
}
