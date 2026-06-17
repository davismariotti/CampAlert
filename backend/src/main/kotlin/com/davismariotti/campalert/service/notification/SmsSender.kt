package com.davismariotti.campalert.service.notification

import com.davismariotti.campalert.service.sms.TwilioConfiguration
import com.twilio.rest.api.v2010.account.Message
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
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
    private val twilioCb by lazy { circuitBreakerRegistry.circuitBreaker("twilio") }

    override fun send(to: String, body: String) {
        twilioCb.executeRunnable {
            Message
                .creator(
                    TwilioPhoneNumber(to),
                    twilioConfiguration.messagingServiceSid,
                    body,
                ).create()
        }
    }
}
