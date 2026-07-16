package com.davismariotti.campalert.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "campfinder.turnstile")
data class TurnstileProperties(
    val secretKey: String = "",
)
