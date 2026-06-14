package com.davismariotti.campalert.health

import com.davismariotti.campalert.service.sms.TwilioConfiguration
import com.twilio.rest.api.v2010.Account
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component

@Component("twilio")
class TwilioHealthIndicator(
    private val twilioConfiguration: TwilioConfiguration,
) : HealthIndicator {
    override fun health(): Health =
        try {
            Account.fetcher(twilioConfiguration.accountSid).fetch()
            Health.up().build()
        } catch (e: Exception) {
            Health.down().withDetail("reason", e.message ?: "unknown error").build()
        }
}
