package com.davismariotti.campalert.service.sms

import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.service.PhoneNumberService
import com.twilio.twiml.MessagingResponse
import com.twilio.twiml.messaging.Body
import com.twilio.twiml.messaging.Message
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SmsWebhookService(
    private val phoneNumberRepository: PhoneNumberRepository,
    private val phoneNumberService: PhoneNumberService,
    private val searchRequestRepository: SearchRequestRepository,
    private val smsConversationService: SmsConversationService,
) {
    @Transactional
    fun handleStop(from: String): String {
        val phoneNumber = phoneNumberRepository.findByPhone(from)
        if (phoneNumber != null) {
            phoneNumberRepository.save(phoneNumber.copy(status = PhoneNumberStatus.OPTED_OUT))
            phoneNumberService.pauseRequestsIfNoVerifiedPhone(phoneNumber.userId)
        }
        return emptyTwiml()
    }

    @Transactional
    fun handleStart(from: String): String {
        val phoneNumber = phoneNumberRepository.findByPhone(from)
        if (phoneNumber != null && phoneNumber.status == PhoneNumberStatus.OPTED_OUT) {
            phoneNumberRepository.save(phoneNumber.copy(status = PhoneNumberStatus.VERIFIED))
            phoneNumberService.resumeRequestsIfVerifiedPhone(phoneNumber.userId)
        }
        return emptyTwiml()
    }

    fun handleHelp(): String =
        twimlMessage(
            "CampAlert: For help visit campfinder.app or email support@campfinder.app. Reply STOP to unsubscribe."
        )

    fun handlePause(from: String): String {
        val contextIds = smsConversationService.getContext(from)
        if (contextIds.isNullOrEmpty()) return emptyTwiml()

        if (contextIds.size == 1) return pauseRequest(from, contextIds[0])

        val requests = contextIds.mapNotNull { searchRequestRepository.findById(it).orElse(null) }
        val lines = requests
            .mapIndexed { idx, req ->
                val endDay = req.startDay.plusDays(req.nights.toLong())
                "${idx + 1}. ${req.campgroundName} ${req.startDay}–$endDay"
            }.joinToString("\n")

        smsConversationService.setAwaiting(from, "PAUSE", contextIds)
        return twimlMessage("Which alert would you like to pause? Reply with a number:\n$lines")
    }

    fun handleAwaitingReply(from: String, index: Int, awaiting: AwaitingContext): String {
        val requestId = awaiting.requestIds.getOrNull(index - 1)
        smsConversationService.clearAwaiting(from)
        if (requestId == null) return emptyTwiml()
        return when (awaiting.intent) {
            "PAUSE" -> pauseRequest(from, requestId)
            else -> emptyTwiml()
        }
    }

    @Transactional
    fun pauseRequest(from: String, requestId: Long): String {
        val request = searchRequestRepository.findById(requestId).orElse(null) ?: return emptyTwiml()
        searchRequestRepository.save(request.copy(userPaused = true))
        return twimlMessage("Alert paused. We'll notify you if ${request.campgroundName} opens a new window.")
    }

    private fun emptyTwiml(): String = MessagingResponse.Builder().build().toXml()

    private fun twimlMessage(text: String): String =
        MessagingResponse
            .Builder()
            .message(Message.Builder().body(Body.Builder(text).build()).build())
            .build()
            .toXml()
}
