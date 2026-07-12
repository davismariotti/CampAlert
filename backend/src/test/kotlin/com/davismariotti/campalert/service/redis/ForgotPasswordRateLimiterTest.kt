package com.davismariotti.campalert.service.redis

import com.davismariotti.campalert.config.ForgotPasswordRateLimitProperties
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Duration
import java.util.concurrent.TimeUnit

class ForgotPasswordRateLimiterTest {
    private val redisJsonCache = mock<RedisJsonCache>()
    private val props = ForgotPasswordRateLimitProperties(rateLimitMaxRequests = 3, rateLimitWindow = Duration.ofMinutes(1))
    private val limiter = ForgotPasswordRateLimiter(redisJsonCache, props)

    private fun stubIncrement(ip: String, result: Long) {
        whenever(
            redisJsonCache.increment(
                eq("ratelimit:forgot-password:$ip"),
                eq(props.rateLimitWindow.toMillis()),
                eq(TimeUnit.MILLISECONDS),
            ),
        ).thenReturn(result)
    }

    @Test
    fun `request within the limit is allowed`() {
        stubIncrement("1.2.3.4", 1L)

        assertTrue(limiter.tryAcquire("1.2.3.4"))
    }

    @Test
    fun `request at the limit is allowed`() {
        stubIncrement("1.2.3.4", 3L)

        assertTrue(limiter.tryAcquire("1.2.3.4"))
    }

    @Test
    fun `request over the limit is rejected`() {
        stubIncrement("1.2.3.4", 4L)

        assertFalse(limiter.tryAcquire("1.2.3.4"))
    }

    @Test
    fun `different client IPs are tracked independently`() {
        stubIncrement("1.2.3.4", 4L)
        stubIncrement("5.6.7.8", 1L)

        assertFalse(limiter.tryAcquire("1.2.3.4"))
        assertTrue(limiter.tryAcquire("5.6.7.8"))
    }
}
