package com.davismariotti.campalert.service.sms

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
    private val smsConversationService: SmsConversationService,
    private val smsWebhookService: SmsWebhookService,
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

        val trimmed = body.trim()
        val keyword = trimmed.uppercase()

        val singleDigit = trimmed.toIntOrNull()
        if (singleDigit != null && singleDigit in 1..9) {
            val awaiting = smsConversationService.getAwaiting(from)
            if (awaiting != null) {
                return twimlResponse(smsWebhookService.handleAwaitingReply(from, singleDigit, awaiting))
            }
            return twimlResponse(smsWebhookService.handleAwaitingReply(from, singleDigit, AwaitingContext("", emptyList())))
        }

        val twiml = when {
            keyword in stopKeywords -> smsWebhookService.handleStop(from)
            keyword in startKeywords -> smsWebhookService.handleStart(from)
            keyword == "HELP" -> smsWebhookService.handleHelp()
            keyword == "PAUSE" -> smsWebhookService.handlePause(from)
            else -> null
        }
        return if (twiml != null) twimlResponse(twiml) else ResponseEntity.ok().contentType(MediaType.TEXT_XML).build()
    }

    private fun twimlResponse(xml: String): ResponseEntity<String> = ResponseEntity.ok().contentType(MediaType.TEXT_XML).body(xml)

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
