package com.davismariotti.campalert.service.redis

import com.davismariotti.campalert.config.ForgotPasswordRateLimitProperties
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Fixed-window request counter keyed by client IP, backing the abuse-resistance backstop on
 * `/auth/forgot-password` (the per-account cooldown in [com.davismariotti.campalert.service.email.PasswordResetService]
 * remains the primary defense against repeated-target abuse; this targets scripted probing across
 * many target emails from one source).
 */
@Component
class ForgotPasswordRateLimiter(
    private val redisJsonCache: RedisJsonCache,
    private val props: ForgotPasswordRateLimitProperties,
) {
    /** Returns true if the request is within the per-IP limit for the current window. */
    fun tryAcquire(clientIp: String): Boolean {
        val key = "ratelimit:forgot-password:$clientIp"
        val count = redisJsonCache.increment(key, props.rateLimitWindow.toMillis(), TimeUnit.MILLISECONDS)
        return count <= props.rateLimitMaxRequests
    }
}
