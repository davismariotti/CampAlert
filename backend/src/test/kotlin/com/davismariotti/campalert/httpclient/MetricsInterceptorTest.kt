package com.davismariotti.campalert.httpclient

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

class MetricsInterceptorTest {
    @Test
    fun `resolveMetricName picks the matching rule`() {
        val rules = listOf(
            Regex("^/camps/") to "Custom/RecreationGov/AvailabilityFetch",
            Regex("permit") to "Custom/RecreationGov/PermitAvailabilityFetch",
        )

        assertEquals(
            "Custom/RecreationGov/AvailabilityFetch",
            MetricsInterceptor.resolveMetricName("/camps/availability/campground/123/month", "Custom/RecreationGov/AvailabilityFetch", rules),
        )
        assertEquals(
            "Custom/RecreationGov/PermitAvailabilityFetch",
            MetricsInterceptor.resolveMetricName("/permits/456/availability/month", "Custom/RecreationGov/AvailabilityFetch", rules),
        )
    }

    @Test
    fun `resolveMetricName falls back to the default when no rule matches`() {
        val rules = listOf(Regex("^/camps/") to "Custom/RecreationGov/AvailabilityFetch")

        assertEquals(
            "Custom/RecreationGov/AvailabilityFetch",
            MetricsInterceptor.resolveMetricName("/search/suggest", "Custom/RecreationGov/AvailabilityFetch", rules),
        )
    }

    @Test
    fun `fixed-name constructor records the same name regardless of request path`() {
        val interceptor = MetricsInterceptor("Custom/Ridb/Request")
        val request = Request.Builder().url("https://ridb.recreation.gov/api/v1/facilities/1").build()
        val chain = mock<Interceptor.Chain>()
        whenever(chain.request()).thenReturn(request)
        whenever(chain.proceed(request)).thenReturn(
            Response
                .Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build(),
        )

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
    }
}
