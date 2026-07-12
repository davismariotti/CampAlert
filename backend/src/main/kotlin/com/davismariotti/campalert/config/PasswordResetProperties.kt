package com.davismariotti.campalert.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "campfinder.email.reset")
data class PasswordResetProperties(
    val expiresIn: Duration = Duration.ofMinutes(15),
    val resendCooldown: Duration = Duration.ofSeconds(60),
    val cleanupRetention: Duration = Duration.ofDays(7),
)
