package com.davismariotti.campalert.service

import com.twilio.Twilio
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
class TwilioConfiguration(
    @Value("\${twilio.account_sid}") val accountSid: String,
    @Value("\${twilio.auth_token}") val authToken: String,
    @Value("\${twilio.from_number}") val fromNumber: String,
    @Value("\${twilio.verify_service_sid}") val verifyServiceSid: String,
) {
    @PostConstruct
    fun init() {
        Twilio.init(accountSid, authToken)
    }
}
