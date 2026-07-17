package com.davismariotti.campalert.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.newrelic.ClientProviderType
import io.micrometer.newrelic.NewRelicConfig
import io.micrometer.newrelic.NewRelicInsightsAgentClientProvider
import io.micrometer.newrelic.NewRelicMeterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Exports every Micrometer meter registered in this app — currently the shared `notifications-java`
 * library's `notifications.send` Timer / `notifications.send.failures` Counter (channel/provider/
 * outcome tags) — to New Relic via [ClientProviderType.INSIGHTS_AGENT], which delegates through the
 * already-attached New Relic Java agent instead of a second Insights API key/account id. Spring Boot
 * composites this alongside its own default `MeterRegistry`, so `NotificationsAutoConfiguration`'s
 * `ObjectProvider<MeterRegistry>.ifAvailable` picks it up with no change to that library.
 *
 * Meters published this way land in NRDB as `MicrometerSample` custom events (attributes:
 * `metricName`, `avg`/`count`/`max`/`totalTime` for a Timer, `throughput` for a Counter, plus the
 * meter's own tags) — not as APM `Metric` type data like the `Custom/RecreationGov/...`-style
 * response-time metrics recorded directly via [com.newrelic.api.agent.NewRelic]. Query with
 * `FROM MicrometerSample WHERE metricName = 'notifications.send' ...`, not `FROM Metric`.
 */
@Configuration
class MicrometerNewRelicConfiguration {
    @Bean
    fun newRelicMeterRegistry(): MeterRegistry {
        val config = object : NewRelicConfig {
            override fun get(key: String): String? = null

            override fun clientProviderType(): ClientProviderType = ClientProviderType.INSIGHTS_AGENT
        }
        return NewRelicMeterRegistry
            .builder(config)
            .clientProvider(NewRelicInsightsAgentClientProvider(config))
            .build()
    }
}
