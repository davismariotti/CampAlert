package com.davismariotti.campalert.config

import net.pushover.client.PushoverRestClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class NotificationConfig {
    @Bean
    fun pushoverClient(): PushoverRestClient = PushoverRestClient()
}
