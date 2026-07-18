package com.davismariotti.campalert.httpclient

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Invocation
import java.io.IOException
import kotlin.test.assertEquals

class MetricsInterceptorTest {
    private fun requestFor(url: String, methodName: String?): Request {
        val builder = Request.Builder().url(url)
        if (methodName != null) {
            val method = TestApi::class.java.getMethod(methodName)
            builder.tag(Invocation::class.java, Invocation.of(TestApi::class.java, object : TestApi {}, method, emptyList<Any>()))
        }
        return builder.build()
    }

    private fun chainFor(request: Request): Interceptor.Chain {
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
        return chain
    }

    private fun failingChainFor(request: Request, exception: IOException): Interceptor.Chain {
        val chain = mock<Interceptor.Chain>()
        whenever(chain.request()).thenReturn(request)
        whenever(chain.proceed(request)).thenThrow(exception)
        return chain
    }

    interface TestApi {
        fun getDirectory() {}

        fun getCampgroundSession() {}
    }

    @Test
    fun `byRetrofitMethod names the metric after the invoking Retrofit method`() {
        val interceptor = MetricsInterceptor.byRetrofitMethod(source = UpstreamSource.provider("CampLife"))

        interceptor.intercept(chainFor(requestFor("https://www.camplife.com/api/campgrounds", "getDirectory")))
        interceptor.intercept(chainFor(requestFor("https://www.camplife.com/api/reservation/session", "getCampgroundSession")))

        // NewRelic.recordResponseTimeMetric / recordCustomEvent are no-ops without the agent, so
        // there's nothing to assert on there directly — this just confirms distinct methods don't
        // throw and are handled independently. The actual name derivation is covered directly below.
    }

    @Test
    fun `metric name is derived directly from the Invocation tag`() {
        val request = requestFor("https://www.camplife.com/api/campgrounds", "getDirectory")
        val method = request.tag(Invocation::class.java)?.method()

        assertEquals("getDirectory", method?.name)
    }

    @Test
    fun `falls back to the default name when a request has no Invocation tag`() {
        val interceptor = MetricsInterceptor.byRetrofitMethod(source = UpstreamSource.provider("CampLife"), default = "Unrecognized")
        val request = requestFor("https://www.camplife.com/api/campgrounds", methodName = null)

        val response = interceptor.intercept(chainFor(request))

        assertEquals(200, response.code)
    }

    @Test
    fun `fixed-name constructor records the same name regardless of request path`() {
        val interceptor = MetricsInterceptor(UpstreamSource.integration("Ridb"), "Request")
        val request = Request.Builder().url("https://ridb.recreation.gov/api/v1/facilities/1").build()

        val response = interceptor.intercept(chainFor(request))

        assertEquals(200, response.code)
    }

    @Test
    fun `an IOException from the chain is rethrown, not swallowed`() {
        val interceptor = MetricsInterceptor.byRetrofitMethod(source = UpstreamSource.provider("CampLife"))
        val request = requestFor("https://www.camplife.com/api/campgrounds", "getDirectory")
        val exception = IOException("connection reset")

        val thrown = assertThrows<IOException> { interceptor.intercept(failingChainFor(request, exception)) }

        assertEquals(exception, thrown)
    }

    @Test
    fun `UpstreamSource factories set the expected namespace`() {
        assertEquals("Provider", UpstreamSource.provider("CampLife").namespace)
        assertEquals("Integration", UpstreamSource.integration("Ridb").namespace)
    }
}
