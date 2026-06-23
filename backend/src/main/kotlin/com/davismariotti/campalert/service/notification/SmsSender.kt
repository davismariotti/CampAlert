package com.davismariotti.campalert.service.notification

import com.davismariotti.campalert.service.sms.TwilioConfiguration
import com.newrelic.api.agent.NewRelic
import com.twilio.rest.api.v2010.account.Message
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import com.twilio.type.PhoneNumber as TwilioPhoneNumber

interface SmsSender {
    fun send(to: String, body: String)
}

@Service
class TwilioSmsSender(
    private val twilioConfiguration: TwilioConfiguration,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
) : SmsSender {
    private val log = LoggerFactory.getLogger(javaClass)
    private val twilioCb by lazy { circuitBreakerRegistry.circuitBreaker("twilio") }

    override fun send(to: String, body: String) {
        val start = System.currentTimeMillis()
        try {
            twilioCb.executeRunnable {
                Message
                    .creator(
                        TwilioPhoneNumber(to),
                        twilioConfiguration.messagingServiceSid,
                        body,
                    ).create()
            }
        } catch (e: Exception) {
            log.warn("Twilio SMS send failed to={}", to, e)
            throw e
        } finally {
            NewRelic.recordResponseTimeMetric("Custom/Twilio/SmsSend", System.currentTimeMillis() - start)
        }
    }
}
