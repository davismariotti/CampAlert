package com.davismariotti.campalert.service.redis

import com.davismariotti.campalert.config.ForgotPasswordRateLimitProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * Fixed-window request counter keyed by client IP, backing the abuse-resistance backstop on
 * `/auth/forgot-password` (the per-account cooldown in [com.davismariotti.campalert.service.email.PasswordResetService]
 * remains the primary defense against repeated-target abuse; this targets scripted probing across
 * many target emails from one source).
 */
@Component
class ForgotPasswordRateLimiter(
    private val redisTemplate: StringRedisTemplate,
    private val props: ForgotPasswordRateLimitProperties,
) {
    /** Returns true if the request is within the per-IP limit for the current window. */
    fun tryAcquire(clientIp: String): Boolean {
        val key = "ratelimit:forgot-password:$clientIp"
        val count = redisTemplate.opsForValue().increment(key) ?: 1L
        if (count == 1L) {
            redisTemplate.expire(key, props.rateLimitWindow)
        }
        return count <= props.rateLimitMaxRequests
    }
}
