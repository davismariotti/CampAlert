package com.davismariotti.campalert.service.scheduling

import com.davismariotti.campalert.config.SearchLimitsProperties
import com.davismariotti.campalert.provider.Provider
import org.springframework.stereotype.Component

/**
 * Per-provider flexible-search limits, keyed by `campfinder.search.providers.<provider-key>.max-range-width-days`
 * where `<provider-key>` is [Provider.name] lowercased with underscores replaced by hyphens (e.g.
 * `RECREATION_GOV` -> `recreation-gov`) — same keying as [ProviderPollingProperties]. Unlike polling
 * interval, there is no default fallback: a provider with no entry does not support flexible search
 * at all, so [maxRangeWidthDaysFor] returns null rather than a global default (see design decision 4).
 */
@Component
class ProviderSearchWindowProperties(
    private val searchLimitsProperties: SearchLimitsProperties,
) {
    val maxNights: Int get() = searchLimitsProperties.maxNights

    fun maxRangeWidthDaysFor(provider: Provider): Int? = searchLimitsProperties.providers[keyFor(provider)]?.maxRangeWidthDays

    private fun keyFor(provider: Provider): String = provider.name.lowercase().replace('_', '-')
}
