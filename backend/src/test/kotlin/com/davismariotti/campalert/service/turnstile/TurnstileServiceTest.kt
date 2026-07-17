package com.davismariotti.campalert.service.turnstile

import com.davismariotti.campalert.config.TurnstileProperties
import com.davismariotti.campalert.provider.CallProtection
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import retrofit2.Call
import retrofit2.Response

class TurnstileServiceTest {
    private val turnstileApi = mock(TurnstileApi::class.java)
    private val callProtection: CallProtection =
        CallProtection
            .Builder("turnstile")
            .circuitBreaker(CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()))
            .retry(RetryRegistry.of(RetryConfig.ofDefaults()))
            .build()
    private val service = TurnstileService(turnstileApi, TurnstileProperties(secretKey = "secret"), callProtection)

    @Suppress("UNCHECKED_CAST")
    private fun mockCall(response: Response<SiteverifyResponse>): Call<SiteverifyResponse> {
        val call = mock(Call::class.java) as Call<SiteverifyResponse>
        `when`(call.execute()).thenReturn(response)
        return call
    }

    @Test
    fun `verified success does not throw`() {
        val call = mockCall(Response.success(SiteverifyResponse(success = true)))
        `when`(turnstileApi.siteverify(anyString(), anyString())).thenReturn(call)

        service.verify("token")
    }

    @Test
    fun `verified failure throws TurnstileFailedException`() {
        val call = mockCall(Response.success(SiteverifyResponse(success = false)))
        `when`(turnstileApi.siteverify(anyString(), anyString())).thenReturn(call)

        assertThrows(TurnstileFailedException::class.java) { service.verify("token") }
    }

    @Test
    fun `network error fails open`() {
        val call = mock(Call::class.java)
        `when`(call.execute()).thenThrow(RuntimeException("timeout"))
        @Suppress("UNCHECKED_CAST")
        `when`(turnstileApi.siteverify(anyString(), anyString())).thenReturn(call as Call<SiteverifyResponse>)

        service.verify("token")
    }

    @Test
    fun `non-2xx response fails open`() {
        val errorBody = "{}".toResponseBody("application/json".toMediaType())
        val call = mockCall(Response.error(502, errorBody))
        `when`(turnstileApi.siteverify(anyString(), anyString())).thenReturn(call)

        service.verify("token")
    }

    @Test
    fun `open circuit breaker still fails open rather than surfacing CallNotPermittedException`() {
        // Low threshold so a couple of real failures are enough to trip it open within this test.
        val lowThresholdCallProtection = CallProtection
            .Builder("turnstile")
            .circuitBreaker(
                CircuitBreakerRegistry.of(
                    CircuitBreakerConfig
                        .custom()
                        .slidingWindowSize(2)
                        .minimumNumberOfCalls(2)
                        .failureRateThreshold(50f)
                        .build(),
                ),
            ).retry(RetryRegistry.of(RetryConfig.custom<Any>().maxAttempts(1).build()))
            .build()
        val serviceWithLowThreshold = TurnstileService(turnstileApi, TurnstileProperties(secretKey = "secret"), lowThresholdCallProtection)
        val call = mock(Call::class.java)
        `when`(call.execute()).thenThrow(RuntimeException("timeout"))
        @Suppress("UNCHECKED_CAST")
        `when`(turnstileApi.siteverify(anyString(), anyString())).thenReturn(call as Call<SiteverifyResponse>)

        // First two calls trip the breaker open (each fails open individually, same as `network error fails open`).
        repeat(2) { serviceWithLowThreshold.verify("token") }

        // Third call is short-circuited by the now-open breaker (CallNotPermittedException) —
        // verify() still fails open rather than letting that exception escape as an unhandled error.
        serviceWithLowThreshold.verify("token")
    }
}
