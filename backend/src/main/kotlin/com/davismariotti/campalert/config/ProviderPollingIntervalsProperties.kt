package com.davismariotti.campalert.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Backs `campfinder.polling.providers.<provider-key>.interval-ms` overrides — see [com.davismariotti.campalert.service.scheduling.ProviderPollingProperties]. */
@ConfigurationProperties(prefix = "campfinder.polling")
data class ProviderPollingIntervalsProperties(
    val providers: Map<String, ProviderPollingInterval> = emptyMap(),
)

data class ProviderPollingInterval(
    val intervalMs: Long,
)
