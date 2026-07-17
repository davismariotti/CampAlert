package com.davismariotti.campalert.httpclient

import com.newrelic.api.agent.NewRelic
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import retrofit2.Invocation

/**
 * Records a New Relic response-time metric for every request/response passing through this
 * interceptor. [metricNameForRequest] resolves the metric name per request — most providers use a
 * single fixed name (see the convenience constructor), but a client shared by several distinct
 * Retrofit API methods (e.g. Recreation.gov's campground-availability vs. permit-availability
 * endpoints) should give every method its own metric via [byRetrofitMethod].
 */
class MetricsInterceptor private constructor(
    private val metricNameForRequest: (Request) -> String
) : Interceptor {
    constructor(metricName: String) : this({ metricName })

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val start = System.currentTimeMillis()
        try {
            return chain.proceed(request)
        } finally {
            NewRelic.recordResponseTimeMetric(metricNameForRequest(request), System.currentTimeMillis() - start)
        }
    }

    companion object {
        /**
         * Names each metric `"$prefix${retrofit method name}"` — e.g. `"Custom/CampLife/"` +
         * `getDirectory` -> `"Custom/CampLife/getDirectory"` — giving every distinct API call its own
         * metric with zero manual per-route mapping. Retrofit tags every request it builds with an
         * [Invocation] carrying the calling method (`RequestFactory`, confirmed independent of any
         * annotation), so this works for any Retrofit-backed client without extra setup at the call
         * site. Falls back to [default] if a request has no such tag (shouldn't happen for a
         * Retrofit-only client, but avoids a crash if a non-Retrofit interceptor is ever mixed in).
         */
        fun byRetrofitMethod(prefix: String, default: String = "Unknown"): MetricsInterceptor = MetricsInterceptor { request -> prefix + (request.tag(Invocation::class.java)?.method()?.name ?: default) }
    }
}
