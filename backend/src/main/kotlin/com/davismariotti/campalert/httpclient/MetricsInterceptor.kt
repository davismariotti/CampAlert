package com.davismariotti.campalert.httpclient

import com.newrelic.api.agent.NewRelic
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import retrofit2.Invocation
import java.io.IOException

/**
 * Identifies what's on the other end of a [MetricsInterceptor]-wrapped call: an actual
 * [com.davismariotti.campalert.provider.Provider] scrape target (Recreation.gov, CampLife) via
 * [provider], or a non-Provider external integration (RIDB, Turnstile) via [integration]. [tag] is
 * written once here and used to derive both the metric-name namespace (`Provider/` / `Integration/`)
 * and the `provider` attribute on the `UpstreamCallCompleted` custom event, so the two can't drift
 * out of sync the way two separately-typed constructor arguments could.
 */
data class UpstreamSource(
    val namespace: String,
    val tag: String
) {
    companion object {
        fun provider(tag: String) = UpstreamSource("Provider", tag)

        fun integration(tag: String) = UpstreamSource("Integration", tag)
    }
}

/**
 * Records New Relic telemetry for every request/response passing through this interceptor:
 *  - a response-time timeslice metric (unchanged from before — backs the existing per-provider
 *    Response Time / Throughput dashboard widgets), named `"${source.namespace}/${source.tag}/$method"`.
 *  - an `UpstreamCallCompleted` custom event carrying source tag/method/status code/success/
 *    duration/exception type, so per-method error rate can be faceted and queried the same way
 *    `PollTargetCheckCompleted` already backs the "Poll Success Rate" widget.
 *
 * `success` reflects HTTP-level success ([Response.isSuccessful], a 2xx status) only — it does not
 * mean "the business outcome was what we wanted" (e.g. RIDB returning 404 for a missing facility is
 * an expected, non-2xx outcome that still counts as a failure here).
 */
class MetricsInterceptor private constructor(
    private val source: UpstreamSource,
    private val methodForRequest: (Request) -> String,
) : Interceptor {
    constructor(source: UpstreamSource, name: String) : this(source, { name })

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val method = methodForRequest(request)
        val start = System.currentTimeMillis()
        var response: Response? = null
        var exception: IOException? = null
        try {
            response = chain.proceed(request)
            return response
        } catch (e: IOException) {
            exception = e
            throw e
        } finally {
            val durationMs = System.currentTimeMillis() - start
            NewRelic.recordResponseTimeMetric("${source.namespace}/${source.tag}/$method", durationMs)
            NewRelic.getAgent().insights.recordCustomEvent(
                "UpstreamCallCompleted",
                mapOf(
                    "provider" to source.tag,
                    "method" to method,
                    "statusCode" to (response?.code ?: 0),
                    "success" to (response?.isSuccessful ?: false),
                    "durationMs" to durationMs,
                    "exceptionType" to (exception?.javaClass?.simpleName ?: ""),
                ),
            )
        }
    }

    companion object {
        /**
         * Names each metric `"${source.namespace}/${source.tag}/${retrofit method name}"` — e.g.
         * `UpstreamSource.provider("CampLife")` + `getDirectory` -> `"Provider/CampLife/getDirectory"`
         * — giving every distinct API call its own metric with zero manual per-route mapping. Retrofit
         * tags every request it builds with an [Invocation] carrying the calling method
         * (`RequestFactory`, confirmed independent of any annotation), so this works for any
         * Retrofit-backed client without extra setup at the call site. Falls back to [default] if a
         * request has no such tag (shouldn't happen for a Retrofit-only client, but avoids a crash if
         * a non-Retrofit interceptor is ever mixed in).
         */
        fun byRetrofitMethod(source: UpstreamSource, default: String = "Unknown"): MetricsInterceptor = MetricsInterceptor(source) { request -> request.tag(Invocation::class.java)?.method()?.name ?: default }
    }
}
