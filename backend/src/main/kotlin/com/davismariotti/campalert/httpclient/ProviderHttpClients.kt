package com.davismariotti.campalert.httpclient

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * A provider's browser-mimicking [OkHttpClient]: its own [InMemoryCookieJar] plus [BrowserHeadersInterceptor]
 * and [MetricsInterceptor] scoped to [refererOrigin]/[metricName]. Every caller gets a fresh instance —
 * never share the result across providers (see [InMemoryCookieJar]'s and [BrowserHeadersInterceptor]'s docs).
 */
fun buildBrowserOkHttpClient(
    refererOrigin: String,
    metricName: String,
    vararg additionalInterceptors: Interceptor,
): OkHttpClient =
    OkHttpClient
        .Builder()
        .cookieJar(InMemoryCookieJar())
        .addInterceptor(BrowserHeadersInterceptor(refererOrigin = refererOrigin))
        .addInterceptor(MetricsInterceptor(metricName))
        .apply { additionalInterceptors.forEach { addInterceptor(it) } }
        .build()

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
