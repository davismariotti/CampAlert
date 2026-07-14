package com.davismariotti.campalert.service.scheduling

import com.davismariotti.campalert.config.ProviderPollingInterval
import com.davismariotti.campalert.config.ProviderPollingIntervalsProperties
import com.davismariotti.campalert.provider.Provider
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ProviderPollingPropertiesTest {
    @Test
    fun `explicitly configured provider uses its own interval`() {
        val properties = ProviderPollingProperties(
            ProviderPollingIntervalsProperties(providers = mapOf("recreation-gov" to ProviderPollingInterval(300_000L))),
            defaultIntervalMs = 120_000L,
        )

        assertEquals(300_000L, properties.intervalFor(Provider.RECREATION_GOV))
    }

    @Test
    fun `provider with no explicit entry falls back to the global default`() {
        val properties = ProviderPollingProperties(
            ProviderPollingIntervalsProperties(providers = emptyMap()),
            defaultIntervalMs = 120_000L,
        )

        assertEquals(120_000L, properties.intervalFor(Provider.RECREATION_GOV))
    }
}
