package com.davismariotti.campalert.httpclient

import com.newrelic.api.agent.NewRelic
import okhttp3.Interceptor
import okhttp3.Response

class MetricsInterceptor(
    private val metricName: String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val start = System.currentTimeMillis()
        try {
            return chain.proceed(chain.request())
        } finally {
            NewRelic.recordResponseTimeMetric(metricName, System.currentTimeMillis() - start)
        }
    }
}
