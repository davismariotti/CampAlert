package com.davismariotti.campalert.service.sms

import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.service.PhoneNumberService
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
    private val searchRequestRepository: SearchRequestRepository,
    private val smsConversationService: SmsConversationService,
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

        // Check awaiting context first: single-digit reply during disambiguation
        val singleDigit = trimmed.toIntOrNull()
        if (singleDigit != null && singleDigit in 1..9) {
            val awaiting = smsConversationService.getAwaiting(from)
            if (awaiting != null) {
                return handleAwaitingReply(from, singleDigit, awaiting)
            }
            // No awaiting context — treat as unrecognized
            return ResponseEntity.ok().contentType(MediaType.TEXT_XML).body(emptyTwiml())
        }

        return when {
            keyword in stopKeywords -> handleStop(from)
            keyword in startKeywords -> handleStart(from)
            keyword == "HELP" -> handleHelp()
            keyword == "PAUSE" -> handlePause(from)
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

    private fun handlePause(from: String): ResponseEntity<String> {
        val contextIds = smsConversationService.getContext(from)
        if (contextIds.isNullOrEmpty()) {
            // No context — silent no-op
            return ResponseEntity.ok().contentType(MediaType.TEXT_XML).body(emptyTwiml())
        }

        if (contextIds.size == 1) {
            return pauseRequest(from, contextIds[0])
        }

        // Multiple requests — need disambiguation
        val requests = contextIds.mapNotNull { searchRequestRepository.findById(it).orElse(null) }
        val lines = requests
            .mapIndexed { idx, req ->
                val endDay = req.startDay.plusDays(req.nights.toLong())
                "${idx + 1}. ${req.campgroundName} ${req.startDay}–$endDay"
            }.joinToString("\n")

        smsConversationService.setAwaiting(from, "PAUSE", contextIds)

        val twiml = twimlMessage("Which alert would you like to pause? Reply with a number:\n$lines")
        return ResponseEntity.ok().contentType(MediaType.TEXT_XML).body(twiml)
    }

    private fun handleAwaitingReply(from: String, index: Int, awaiting: AwaitingContext): ResponseEntity<String> {
        val requestId = awaiting.requestIds.getOrNull(index - 1)
        smsConversationService.clearAwaiting(from)

        if (requestId == null) {
            return ResponseEntity.ok().contentType(MediaType.TEXT_XML).body(emptyTwiml())
        }

        return when (awaiting.intent) {
            "PAUSE" -> pauseRequest(from, requestId)
            else -> ResponseEntity.ok().contentType(MediaType.TEXT_XML).body(emptyTwiml())
        }
    }

    private fun pauseRequest(from: String, requestId: Int): ResponseEntity<String> {
        val request = searchRequestRepository.findById(requestId).orElse(null)
            ?: return ResponseEntity.ok().contentType(MediaType.TEXT_XML).body(emptyTwiml())

        searchRequestRepository.save(request.copy(userPaused = true))

        val twiml = twimlMessage(
            "Alert paused. We'll notify you if ${request.campgroundName} opens a new window."
        )
        return ResponseEntity.ok().contentType(MediaType.TEXT_XML).body(twiml)
    }

    private fun emptyTwiml() =
        """<?xml version="1.0" encoding="UTF-8"?>
<Response></Response>"""

    private fun twimlMessage(message: String): String =
        """<?xml version="1.0" encoding="UTF-8"?>
<Response>
  <Message>${message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</Message>
</Response>"""

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
