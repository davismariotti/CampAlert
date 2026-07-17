package com.davismariotti.campalert.httpclient

import com.davismariotti.campalert.provider.Provider
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Per-vendor HTTP client timeout overrides, e.g. `campfinder.http-client.read-timeout.camplife=60s`.
 * A vendor/type combination left unset falls back to [ProviderHttpClientFactory]'s default.
 */
@ConfigurationProperties(prefix = "campfinder.http-client")
data class ProviderHttpClientProperties(
    val connectTimeout: Map<String, Duration> = emptyMap(),
    val readTimeout: Map<String, Duration> = emptyMap(),
    val writeTimeout: Map<String, Duration> = emptyMap(),
)

/**
 * Builds each provider's browser-mimicking [OkHttpClient]: its own [InMemoryCookieJar] plus
 * [BrowserHeadersInterceptor] and [MetricsInterceptor] scoped to `refererOrigin`/`metricName`, with
 * per-[provider] timeout overrides (keyed by [Provider.configName]) resolved from
 * [ProviderHttpClientProperties] (30s default). Every [build] call returns a fresh instance — never
 * share the result across providers (see [InMemoryCookieJar]'s and [BrowserHeadersInterceptor]'s docs).
 */
@Component
class ProviderHttpClientFactory(
    private val timeouts: ProviderHttpClientProperties
) {
    fun build(
        provider: Provider,
        refererOrigin: String,
        metricName: String,
        vararg additionalInterceptors: Interceptor,
    ): OkHttpClient = build(provider, refererOrigin, MetricsInterceptor(metricName), *additionalInterceptors)

    fun build(
        provider: Provider,
        refererOrigin: String,
        metricsInterceptor: MetricsInterceptor,
        vararg additionalInterceptors: Interceptor,
    ): OkHttpClient {
        val vendor = provider.configName()
        return OkHttpClient
            .Builder()
            .cookieJar(InMemoryCookieJar())
            .addInterceptor(BrowserHeadersInterceptor(refererOrigin = refererOrigin))
            .addInterceptor(metricsInterceptor)
            .apply { additionalInterceptors.forEach { addInterceptor(it) } }
            .connectTimeout(timeouts.connectTimeout[vendor] ?: DEFAULT_TIMEOUT)
            .readTimeout(timeouts.readTimeout[vendor] ?: DEFAULT_TIMEOUT)
            .writeTimeout(timeouts.writeTimeout[vendor] ?: DEFAULT_TIMEOUT)
            .build()
    }

    /**
     * For integrations that aren't a [Provider] (no campsite/permit booking link) and don't need
     * [InMemoryCookieJar]/[BrowserHeadersInterceptor]'s browser-mimicking behavior — e.g. Cloudflare
     * Turnstile, a legitimate first-party API call rather than a scrape target. [vendor] is the same
     * kebab-case key [Provider.configName] produces, used to look up per-vendor timeout overrides.
     * Attaches only [MetricsInterceptor] and per-vendor timeouts.
     */
    fun buildSimple(
        vendor: String,
        metricsInterceptor: MetricsInterceptor,
        vararg additionalInterceptors: Interceptor,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(metricsInterceptor)
            .apply { additionalInterceptors.forEach { addInterceptor(it) } }
            .connectTimeout(timeouts.connectTimeout[vendor] ?: DEFAULT_TIMEOUT)
            .readTimeout(timeouts.readTimeout[vendor] ?: DEFAULT_TIMEOUT)
            .writeTimeout(timeouts.writeTimeout[vendor] ?: DEFAULT_TIMEOUT)
            .build()

    companion object {
        private val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}

/**
 * The Jackson baseline every provider's Retrofit client starts from — Kotlin support, plus
 * tolerating fields these undocumented, unversioned provider APIs add without warning. Callers
 * customize further (naming strategy, extra modules) as their provider needs; this must stay
 * separate from the app's own camelCase Spring `ObjectMapper` (see CLAUDE.md's critical constraints).
 */
fun baseProviderObjectMapper(): ObjectMapper =
    jacksonObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
