package com.davismariotti.campalert.httpclient

import com.newrelic.api.agent.NewRelic
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Records a New Relic response-time metric for every request/response passing through this
 * interceptor. [metricNameForRequest] resolves the metric name per request — most providers use a
 * single fixed name (see the convenience constructor), but a client shared by several distinct call
 * types (e.g. Recreation.gov's campground-availability vs. permit-availability endpoints) can vary
 * the name by request path via [byPath].
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
         * Picks the metric name by matching the request's URL path against [rules] in order (first
         * match wins), falling back to [default] if none match. Match on path *shape* (regex against
         * the fixed route segments), never a raw literal path — a request path can embed a
         * campsite/permit/facility id, and matching those directly would blow up metric cardinality.
         */
        fun byPath(default: String, vararg rules: Pair<Regex, String>): MetricsInterceptor = MetricsInterceptor { request -> resolveMetricName(request.url.encodedPath, default, rules.asList()) }

        /** Extracted so the naming logic is testable without exercising the OkHttp interceptor chain. */
        internal fun resolveMetricName(path: String, default: String, rules: List<Pair<Regex, String>>): String = rules.firstOrNull { (pattern, _) -> pattern.containsMatchIn(path) }?.second ?: default
    }
}
