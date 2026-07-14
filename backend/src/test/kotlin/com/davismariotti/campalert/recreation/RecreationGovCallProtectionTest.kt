package com.davismariotti.campalert.recreation

import com.davismariotti.campalert.provider.recreation.RecreationGovCallProtection
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class RecreationGovCallProtectionTest {
    private fun protection(limitForPeriod: Int, refreshPeriod: Duration, timeout: Duration): RecreationGovCallProtection {
        val circuitBreakerRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
        val retryRegistry = RetryRegistry.of(RetryConfig.custom<Any>().maxAttempts(1).build())
        val rateLimiterRegistry = RateLimiterRegistry.of(
            RateLimiterConfig
                .custom()
                .limitForPeriod(limitForPeriod)
                .limitRefreshPeriod(refreshPeriod)
                .timeoutDuration(timeout)
                .build(),
        )
        return RecreationGovCallProtection(circuitBreakerRegistry, retryRegistry, rateLimiterRegistry)
    }

    @Test
    fun `a call within the rate limit succeeds immediately`() {
        val protection = protection(limitForPeriod = 5, refreshPeriod = Duration.ofSeconds(1), timeout = Duration.ofSeconds(1))

        val result = protection.execute { "ok" }

        assertEquals("ok", result)
    }

    @Test
    fun `a call waits for the next refresh and proceeds once a permit frees up`() {
        // 1 permit per 100ms window, 2s timeout — the second call has to wait roughly one refresh
        // period, but comfortably within the timeout, so it should succeed rather than fail.
        val protection = protection(limitForPeriod = 1, refreshPeriod = Duration.ofMillis(100), timeout = Duration.ofSeconds(2))
        val calls = AtomicInteger(0)

        protection.execute { calls.incrementAndGet() }
        protection.execute { calls.incrementAndGet() }

        assertEquals(2, calls.get())
    }

    @Test
    fun `an exhausted rate limit past the timeout throws RequestNotPermitted without ever invoking the wrapped call`() {
        // 1 permit per 10s window, 50ms timeout — the second call can't wait long enough for a refresh.
        val protection = protection(limitForPeriod = 1, refreshPeriod = Duration.ofSeconds(10), timeout = Duration.ofMillis(50))
        val calls = AtomicInteger(0)

        protection.execute { calls.incrementAndGet() }
        assertThrows(RequestNotPermitted::class.java) {
            protection.execute { calls.incrementAndGet() }
        }

        // Only the first (permitted) call actually ran the wrapped supplier — proves the rate
        // limiter is the outermost layer and a timeout never reaches retry/circuit-breaker/the
        // real call at all.
        assertEquals(1, calls.get())
    }

    @Test
    fun `circuit breaker still opens on repeated real failures independent of the rate limiter`() {
        val circuitBreakerRegistry = CircuitBreakerRegistry.of(
            CircuitBreakerConfig
                .custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50f)
                .build(),
        )
        val retryRegistry = RetryRegistry.of(RetryConfig.custom<Any>().maxAttempts(1).build())
        val rateLimiterRegistry = RateLimiterRegistry.of(
            RateLimiterConfig
                .custom()
                .limitForPeriod(1000)
                .limitRefreshPeriod(Duration.ofMillis(1))
                .timeoutDuration(Duration.ofSeconds(1))
                .build(),
        )
        val protection = RecreationGovCallProtection(circuitBreakerRegistry, retryRegistry, rateLimiterRegistry)

        repeat(2) {
            try {
                protection.execute { throw RuntimeException("boom") }
            } catch (_: Exception) {
                // expected
            }
        }

        // Third call should be short-circuited by the now-open circuit breaker rather than
        // re-attempting the real (failing) call.
        val exception = assertThrows(Exception::class.java) {
            protection.execute { throw RuntimeException("should not run") }
        }
        assertEquals("io.github.resilience4j.circuitbreaker.CallNotPermittedException", exception::class.qualifiedName)
    }
}
