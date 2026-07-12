package com.davismariotti.campalert.service.redis

import com.davismariotti.campalert.config.ForgotPasswordRateLimitProperties
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

class ForgotPasswordRateLimiterTest {
    private val redisTemplate = mock(StringRedisTemplate::class.java)

    @Suppress("UNCHECKED_CAST")
    private val valueOps = mock(ValueOperations::class.java) as ValueOperations<String, String>
    private val props = ForgotPasswordRateLimitProperties(rateLimitMaxRequests = 3, rateLimitWindow = Duration.ofMinutes(1))
    private val limiter = ForgotPasswordRateLimiter(redisTemplate, props)

    @BeforeEach
    fun setUp() {
        `when`(redisTemplate.opsForValue()).thenReturn(valueOps)
    }

    @Test
    fun `first request in a window sets the TTL and is allowed`() {
        `when`(valueOps.increment("ratelimit:forgot-password:1.2.3.4")).thenReturn(1L)

        assertTrue(limiter.tryAcquire("1.2.3.4"))

        verify(redisTemplate).expire(eq("ratelimit:forgot-password:1.2.3.4"), eq(props.rateLimitWindow))
    }

    @Test
    fun `requests within the limit do not reset the TTL`() {
        `when`(valueOps.increment("ratelimit:forgot-password:1.2.3.4")).thenReturn(2L)

        assertTrue(limiter.tryAcquire("1.2.3.4"))

        verify(redisTemplate, never()).expire(eq("ratelimit:forgot-password:1.2.3.4"), org.mockito.ArgumentMatchers.any())
    }

    @Test
    fun `request at the limit is allowed`() {
        `when`(valueOps.increment("ratelimit:forgot-password:1.2.3.4")).thenReturn(3L)

        assertTrue(limiter.tryAcquire("1.2.3.4"))
    }

    @Test
    fun `request over the limit is rejected`() {
        `when`(valueOps.increment("ratelimit:forgot-password:1.2.3.4")).thenReturn(4L)

        assertFalse(limiter.tryAcquire("1.2.3.4"))
    }

    @Test
    fun `different client IPs are tracked independently`() {
        `when`(valueOps.increment("ratelimit:forgot-password:1.2.3.4")).thenReturn(4L)
        `when`(valueOps.increment("ratelimit:forgot-password:5.6.7.8")).thenReturn(1L)

        assertFalse(limiter.tryAcquire("1.2.3.4"))
        assertTrue(limiter.tryAcquire("5.6.7.8"))
    }
}
