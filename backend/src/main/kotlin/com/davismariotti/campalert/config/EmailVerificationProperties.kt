package com.davismariotti.campalert.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "campfinder.email.verification")
data class EmailVerificationProperties(
    val expiresIn: Duration = Duration.ofMinutes(10),
    val resendCooldown: Duration = Duration.ofSeconds(60),
    val maxAttempts: Int = 5,
)
