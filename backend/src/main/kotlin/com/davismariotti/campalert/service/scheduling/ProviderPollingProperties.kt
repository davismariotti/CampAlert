package com.davismariotti.campalert.service.scheduling

import com.davismariotti.campalert.config.ProviderPollingIntervalsProperties
import com.davismariotti.campalert.provider.Provider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Per-provider polling interval, keyed by `campfinder.polling.providers.<provider-key>.interval-ms`
 * where `<provider-key>` is [Provider.name] lowercased with underscores replaced by hyphens (e.g.
 * `RECREATION_GOV` -> `recreation-gov`). A provider with no explicit entry falls back to the global
 * `campfinder.polling.interval-ms` — see design decision 5.
 */
@Component
class ProviderPollingProperties(
    private val providerPollingIntervalsProperties: ProviderPollingIntervalsProperties,
    @param:Value($$"${campfinder.polling.interval-ms}") private val defaultIntervalMs: Long,
) {
    fun intervalFor(provider: Provider): Long = providerPollingIntervalsProperties.providers[keyFor(provider)]?.intervalMs ?: defaultIntervalMs

    private fun keyFor(provider: Provider): String = provider.name.lowercase().replace('_', '-')
}
