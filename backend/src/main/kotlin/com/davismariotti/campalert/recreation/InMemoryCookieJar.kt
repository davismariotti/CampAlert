package com.davismariotti.campalert.recreation

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * A real browser tab persists cookies (e.g. load-balancer affinity) across requests; without a
 * cookie jar every call looks like a brand-new, cookie-less client instead of a returning visitor.
 * OkHttp 5.x moved `JavaNetCookieJar` to a separate artifact, so this is a minimal in-memory
 * equivalent scoped to this one process — persistence across restarts isn't needed here.
 */
class InMemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, List<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        store[url.host] = cookies
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = store[url.host] ?: emptyList()
}
