package com.davismariotti.campalert.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Backs `campfinder.search.max-nights` and `campfinder.search.providers.<provider-key>.max-range-width-days` — see [com.davismariotti.campalert.service.scheduling.ProviderSearchWindowProperties]. */
@ConfigurationProperties(prefix = "campfinder.search")
data class SearchLimitsProperties(
    val maxNights: Int,
    val providers: Map<String, ProviderSearchWindow> = emptyMap(),
)

data class ProviderSearchWindow(
    val maxRangeWidthDays: Int,
)
