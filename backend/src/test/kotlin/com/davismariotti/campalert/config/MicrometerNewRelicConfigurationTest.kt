package com.davismariotti.campalert.config

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Timer
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

/**
 * The New Relic agent's Insights API is a no-op without a real attached agent (same as every other
 * `NewRelic.*` call in this codebase — see the newrelic-custom-instrumentation spec), so this only
 * verifies the local Micrometer plumbing: the registry accepts meters and records against them
 * without throwing. Confirming data actually reaches NRDB (as a `MicrometerSample` custom event,
 * per design.md D4) requires running with a real agent + `NEW_RELIC_LICENSE_KEY` and checking NRQL.
 */
class MicrometerNewRelicConfigurationTest {
    @Test
    fun `newRelicMeterRegistry accepts a Timer and a Counter without throwing`() {
        val registry = MicrometerNewRelicConfiguration().newRelicMeterRegistry()

        val timer = Timer
            .builder("notifications.send")
            .tag("channel", "sms")
            .tag("provider", "twilio")
            .tag("outcome", "success")
            .register(registry)
        timer.record(java.time.Duration.ofMillis(120))

        val counter = Counter
            .builder("notifications.send.failures")
            .tag("channel", "sms")
            .tag("provider", "twilio")
            .tag("retryable", "true")
            .register(registry)
        counter.increment()

        // Both meters are Micrometer Step* implementations under this registry — count()/totalTime()
        // reflect the last *completed* step (default 1 minute), not the still-open current one, so
        // asserting on those values here would be asserting on an artifact of timing rather than
        // behavior. Registration succeeding (and record/increment not throwing) is what a no-op-agent
        // environment (this test suite) can actually verify.
        assertNotNull(registry.find("notifications.send").timer())
        assertNotNull(registry.find("notifications.send.failures").counter())
    }
}
