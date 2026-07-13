package com.davismariotti.campalert.httpclient

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * A real browser tab persists cookies (e.g. load-balancer affinity) across requests; without a
 * cookie jar every call looks like a brand-new, cookie-less client instead of a returning visitor.
 * OkHttp 5.x moved `JavaNetCookieJar` to a separate artifact, so this is a minimal in-memory
 * equivalent scoped to this one process — persistence across restarts isn't needed here.
 *
 * Every provider's HTTP client config constructs its own instance of this class and passes it only
 * to that provider's own `OkHttpClient.Builder()` — it must never become a shared Spring singleton,
 * or cookies from one provider's calls would leak into another's.
 */
class InMemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, List<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        store[url.host] = cookies
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = store[url.host] ?: emptyList()
}
