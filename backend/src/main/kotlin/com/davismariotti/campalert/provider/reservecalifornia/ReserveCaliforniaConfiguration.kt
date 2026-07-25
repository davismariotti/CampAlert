package com.davismariotti.campalert.provider.reservecalifornia

import com.davismariotti.campalert.httpclient.MetricsInterceptor
import com.davismariotti.campalert.httpclient.ProviderHttpClientFactory
import com.davismariotti.campalert.httpclient.UpstreamSource
import com.davismariotti.campalert.httpclient.baseProviderObjectMapper
import com.davismariotti.campalert.provider.CallProtection
import com.davismariotti.campalert.provider.Provider
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import io.github.resilience4j.retry.RetryRegistry
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import java.net.InetSocketAddress
import java.net.Proxy

private const val RESERVE_CALIFORNIA_ORIGIN = "https://www.reservecalifornia.com"

@Configuration
class ReserveCaliforniaConfiguration(
    @Value("\${reservecalifornia.baseUrl}") val baseUrl: String,
    @Value("\${campfinder.reservecalifornia.proxy.host:}") private val proxyHost: String,
    @Value("\${campfinder.reservecalifornia.proxy.port:0}") private val proxyPort: Int,
    private val providerHttpClientFactory: ProviderHttpClientFactory,
) {
    /** Every ReserveCalifornia call requires this header (design.md Appendix "Required headers") — verified live, not documented anywhere by ReserveCalifornia itself. */
    private val tenantIdInterceptor = Interceptor { chain ->
        chain.proceed(
            chain
                .request()
                .newBuilder()
                .header("tenantid", "cali")
                .build()
        )
    }

    /** Extracted from [getReserveCaliforniaClient] so tests can verify cookie-jar isolation without a Spring context. Never call this from a shared/singleton context; each caller gets an independent cookie store. */
    internal fun buildOkHttpClient(): OkHttpClient =
        providerHttpClientFactory.build(
            provider = Provider.RESERVE_CALIFORNIA,
            refererOrigin = RESERVE_CALIFORNIA_ORIGIN,
            metricsInterceptor = MetricsInterceptor.byRetrofitMethod(source = UpstreamSource.provider("ReserveCalifornia")),
            additionalInterceptors = arrayOf(tenantIdInterceptor),
            proxy = reserveCaliforniaProxy(),
        )

    /**
     * ReserveCalifornia's origin blocks this app's production droplet at the CDN/WAF layer —
     * confirmed live: identical headers get HTTP 200 from a residential IP and HTTP 403
     * (`x-cache: Error from cloudfront`, blocked before ever reaching origin) from the droplet, and
     * the block held across every header/protocol/client variant tested, so it's IP-reputation-based,
     * not fixable by anything on the request itself. Routes only ReserveCalifornia's own traffic
     * through a self-hosted VPN egress (gluetun/ProtonVPN — `~/services/docker-compose-gluetun.yml`
     * on the droplet) when `campfinder.reservecalifornia.proxy.host`/`.port` are set; every other
     * provider is unaffected and keeps using the droplet's own egress directly. Left unset in
     * environments where the direct connection isn't blocked (e.g. local dev).
     */
    private fun reserveCaliforniaProxy(): Proxy? =
        if (proxyHost.isBlank() || proxyPort <= 0) {
            null
        } else {
            Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort))
        }

    /**
     * Two independent call-protection instances (design.md D19): [reserveCaliforniaCallProtection] for
     * steady-state polling/catalog calls, [reserveCaliforniaWarmupCallProtection] for the occupancy
     * warm-up fan-out (10 req/s — see `reserve-california-unit-occupancy-warmup` spec). Kept separate
     * from each other so a large facility's warm-up burst can never starve steady-state polling for
     * other ReserveCalifornia facilities, and both are kept independent from Recreation.gov's/CampLife's
     * call protection so a ReserveCalifornia outage/rate-limit can never open their circuits either —
     * same reasoning CampLifeConfiguration's own call protection already documents for itself.
     */
    @Bean
    fun reserveCaliforniaCallProtection(
        circuitBreakerRegistry: CircuitBreakerRegistry,
        retryRegistry: RetryRegistry,
        rateLimiterRegistry: RateLimiterRegistry,
    ): CallProtection =
        CallProtection
            .Builder(Provider.RESERVE_CALIFORNIA)
            .circuitBreaker(circuitBreakerRegistry)
            .retry(retryRegistry)
            .rateLimiter(rateLimiterRegistry, timeoutEventName = "ReserveCaliforniaRateLimitTimeout")
            .build()

    @Bean
    fun reserveCaliforniaWarmupCallProtection(
        circuitBreakerRegistry: CircuitBreakerRegistry,
        retryRegistry: RetryRegistry,
        rateLimiterRegistry: RateLimiterRegistry,
    ): CallProtection =
        CallProtection
            .Builder("reserve-california-warmup")
            .circuitBreaker(circuitBreakerRegistry)
            .retry(retryRegistry)
            .rateLimiter(rateLimiterRegistry, timeoutEventName = "ReserveCaliforniaWarmupRateLimitTimeout")
            .build()

    @Bean
    fun getReserveCaliforniaClient(): ReserveCaliforniaApi {
        // ReserveCalifornia's JSON uses PascalCase keys (FacilityId, IsFiltered, ...) — its own
        // ObjectMapper, never the app's shared camelCase Spring ObjectMapper (CLAUDE.md's critical
        // constraint), mirroring RecreationConfiguration's inline snake_case mapper.
        val objectMapper = baseProviderObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE)
        val retrofit = Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(buildOkHttpClient())
            .addConverterFactory(JacksonConverterFactory.create(objectMapper))
            .build()
        return retrofit.create(ReserveCaliforniaApi::class.java)
    }
}
