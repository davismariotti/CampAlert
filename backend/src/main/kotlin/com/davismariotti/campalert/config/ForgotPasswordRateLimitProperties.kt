package com.davismariotti.campalert.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "campfinder.auth.forgot-password")
data class ForgotPasswordRateLimitProperties(
    val rateLimitMaxRequests: Long = 10,
    val rateLimitWindow: Duration = Duration.ofMinutes(1),
)
