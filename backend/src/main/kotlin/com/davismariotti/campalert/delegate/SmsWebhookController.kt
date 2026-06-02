package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.service.PhoneNumberService
import com.davismariotti.campalert.service.TwilioConfiguration
import com.twilio.security.RequestValidator
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/sms")
class SmsWebhookController(
    private val twilioConfiguration: TwilioConfiguration,
    private val phoneNumberRepository: PhoneNumberRepository,
    private val phoneNumberService: PhoneNumberService,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val stopKeywords = setOf("STOP", "STOPALL", "UNSUBSCRIBE", "CANCEL", "END", "QUIT")
    private val startKeywords = setOf("UNSTOP", "START", "YES")

    @PostMapping("/webhook", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun handleInbound(
        @RequestParam(value = "Body", defaultValue = "") body: String,
        @RequestParam(value = "From", defaultValue = "") from: String,
        request: HttpServletRequest,
    ): ResponseEntity<String> {
        if (!validateSignature(request)) {
            log.warn("Invalid Twilio signature on SMS webhook")
            return ResponseEntity.status(403).build()
        }

        val keyword = body.trim().uppercase()
        return when {
            keyword in stopKeywords -> handleStop(from)
            keyword in startKeywords -> handleStart(from)
            keyword == "HELP" -> handleHelp()
            else -> ResponseEntity.ok().contentType(MediaType.TEXT_XML).body(emptyTwiml())
        }
    }

    private fun handleStop(from: String): ResponseEntity<String> {
        val phoneNumber = phoneNumberRepository.findByPhone(from)
        if (phoneNumber != null) {
            phoneNumberRepository.save(phoneNumber.copy(status = PhoneNumberStatus.OPTED_OUT))
            phoneNumberService.pauseRequestsIfNoVerifiedPhone(phoneNumber.userId)
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_XML).body(emptyTwiml())
    }

    private fun handleStart(from: String): ResponseEntity<String> {
        val phoneNumber = phoneNumberRepository.findByPhone(from)
        if (phoneNumber != null && phoneNumber.status == PhoneNumberStatus.OPTED_OUT) {
            phoneNumberRepository.save(phoneNumber.copy(status = PhoneNumberStatus.VERIFIED))
            phoneNumberService.resumeRequestsIfVerifiedPhone(phoneNumber.userId)
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_XML).body(emptyTwiml())
    }

    private fun handleHelp(): ResponseEntity<String> {
        val twiml =
            """<?xml version="1.0" encoding="UTF-8"?>
<Response>
  <Message>CampAlert: For help visit campfinder.app or email support@campfinder.app. Reply STOP to unsubscribe.</Message>
</Response>"""
        return ResponseEntity.ok().contentType(MediaType.TEXT_XML).body(twiml)
    }

    private fun emptyTwiml() =
        """<?xml version="1.0" encoding="UTF-8"?>
<Response></Response>"""

    private fun validateSignature(request: HttpServletRequest): Boolean {
        val validator = RequestValidator(twilioConfiguration.authToken)
        val url = buildUrl(request)
        val params = request.parameterMap.mapValues { it.value.firstOrNull() ?: "" }
        val signature = request.getHeader("X-Twilio-Signature") ?: return false
        return validator.validate(url, params, signature)
    }

    private fun buildUrl(request: HttpServletRequest): String {
        val url = StringBuilder("${request.scheme}://${request.serverName}")
        if (request.serverPort != 80 && request.serverPort != 443) {
            url.append(":${request.serverPort}")
        }
        url.append(request.requestURI)
        if (request.queryString != null) url.append("?${request.queryString}")
        return url.toString()
    }
}
