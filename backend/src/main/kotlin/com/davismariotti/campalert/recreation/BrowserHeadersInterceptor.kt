package com.davismariotti.campalert.recreation

import okhttp3.Interceptor
import okhttp3.Response

private val DESKTOP_USER_AGENTS = listOf(
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:127.0) Gecko/20100101 Firefox/127.0",
)

/**
 * Recreation.gov's own web app calls this same API from a browser tab; without these headers every
 * request instead carries the bare default OkHttp User-Agent and no Referer/Origin, which is a far
 * easier signal for bot detection to key on than a plausible (if not perfectly matched) browser profile.
 *
 * The User-Agent is picked once per interceptor instance (effectively once per process lifetime,
 * since this is built as a single OkHttpClient) rather than per request — a real browser tab never
 * changes identity between calls in the same session, so rotating per-request combined with a
 * persistent cookie jar would be a stronger tell than not rotating at all.
 */
class BrowserHeadersInterceptor : Interceptor {
    private val userAgent = DESKTOP_USER_AGENTS.random()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain
            .request()
            .newBuilder()
            .header("User-Agent", userAgent)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", "https://www.recreation.gov/")
            .header("Origin", "https://www.recreation.gov")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Site", "same-origin")
            .header("Sec-Fetch-Dest", "empty")
            .build()
        return chain.proceed(request)
    }
}
