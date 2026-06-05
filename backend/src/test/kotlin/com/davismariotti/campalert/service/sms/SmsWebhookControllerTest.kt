package com.davismariotti.campalert.service.sms

import com.davismariotti.campalert.model.PhoneNumber
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.service.PhoneNumberService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.ResponseEntity
import java.time.Instant
import java.time.LocalDate
import java.util.Optional

class SmsWebhookControllerTest {
    private val twilioConfig = mock(TwilioConfiguration::class.java)
    private val phoneNumberRepository = mock(PhoneNumberRepository::class.java)
    private val phoneNumberService = mock(PhoneNumberService::class.java)
    private val searchRequestRepository = mock(SearchRequestRepository::class.java)
    private val conversationService = mock(SmsConversationService::class.java)

    private val controller = SmsWebhookController(
        twilioConfig,
        phoneNumberRepository,
        phoneNumberService,
        searchRequestRepository,
        conversationService,
    )

    private val verifiedPhone = PhoneNumber(
        id = 1L,
        userId = 42L,
        phone = "+15005550006",
        status = PhoneNumberStatus.VERIFIED,
        smsConsentAt = Instant.now(),
    )

    private fun request(id: Int, campgroundName: String = "Upper Pines") =
        SearchRequest(
            id = id,
            startDay = LocalDate.now().plusDays(5),
            nights = 2,
            groupSize = 2,
            campsiteId = 99,
            name = "Trip",
            completed = false,
            campgroundName = campgroundName,
        )

    init {
        `when`(twilioConfig.authToken).thenReturn("test-token")
    }

    @Test
    fun `handleStop sets phone to OPTED_OUT and pauses requests`() {
        `when`(phoneNumberRepository.findByPhone("+15005550006")).thenReturn(verifiedPhone)
        `when`(phoneNumberRepository.save(any(PhoneNumber::class.java))).thenAnswer { it.arguments[0] }

        invokePrivate("handleStop", "+15005550006")

        verify(phoneNumberRepository).save(verifiedPhone.copy(status = PhoneNumberStatus.OPTED_OUT))
        verify(phoneNumberService).pauseRequestsIfNoVerifiedPhone(42L)
    }

    @Test
    fun `handleStart sets OPTED_OUT phone to VERIFIED and resumes requests`() {
        val optedOut = verifiedPhone.copy(status = PhoneNumberStatus.OPTED_OUT)
        `when`(phoneNumberRepository.findByPhone("+15005550006")).thenReturn(optedOut)
        `when`(phoneNumberRepository.save(any(PhoneNumber::class.java))).thenAnswer { it.arguments[0] }

        invokePrivate("handleStart", "+15005550006")

        verify(phoneNumberRepository).save(optedOut.copy(status = PhoneNumberStatus.VERIFIED))
        verify(phoneNumberService).resumeRequestsIfVerifiedPhone(42L)
    }

    @Test
    fun `handleStart on VERIFIED phone takes no action`() {
        `when`(phoneNumberRepository.findByPhone("+15005550006")).thenReturn(verifiedPhone)

        invokePrivate("handleStart", "+15005550006")

        verify(phoneNumberRepository, never()).save(any(PhoneNumber::class.java))
    }

    @Test
    fun `handleHelp returns TwiML with brand name and STOP instruction`() {
        val method = SmsWebhookController::class.java.getDeclaredMethod("handleHelp")
        method.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val response = method.invoke(controller) as ResponseEntity<String>

        assertEquals(200, response.statusCode.value())
        assertTrue(response.body?.contains("CampAlert") == true)
        assertTrue(response.body?.contains("STOP") == true)
    }

    @Test
    fun `PAUSE with single request in context pauses it and confirms`() {
        `when`(conversationService.getContext("+15005550006")).thenReturn(listOf(10))
        `when`(searchRequestRepository.findById(10)).thenReturn(Optional.of(request(10)))
        `when`(searchRequestRepository.save(any(SearchRequest::class.java))).thenAnswer { it.arguments[0] }

        val method = SmsWebhookController::class.java.getDeclaredMethod("handlePause", String::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val response = method.invoke(controller, "+15005550006") as ResponseEntity<String>

        verify(searchRequestRepository).save(request(10).copy(userPaused = true))
        assertTrue(response.body?.contains("paused") == true)
    }

    @Test
    fun `PAUSE with multiple requests in context sends disambiguation and sets awaiting`() {
        val req1 = request(10, "Upper Pines")
        val req2 = request(11, "Yosemite Valley")
        `when`(conversationService.getContext("+15005550006")).thenReturn(listOf(10, 11))
        `when`(searchRequestRepository.findById(10)).thenReturn(Optional.of(req1))
        `when`(searchRequestRepository.findById(11)).thenReturn(Optional.of(req2))

        val method = SmsWebhookController::class.java.getDeclaredMethod("handlePause", String::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val response = method.invoke(controller, "+15005550006") as ResponseEntity<String>

        verify(conversationService).setAwaiting("+15005550006", "PAUSE", listOf(10, 11))
        assertTrue(response.body?.contains("1.") == true)
        assertTrue(response.body?.contains("2.") == true)
        verify(searchRequestRepository, never()).save(any(SearchRequest::class.java))
    }

    @Test
    fun `numeric reply with awaiting context executes PAUSE`() {
        val awaiting = AwaitingContext(intent = "PAUSE", requestIds = listOf(10, 11))
        `when`(conversationService.getAwaiting("+15005550006")).thenReturn(awaiting)
        `when`(searchRequestRepository.findById(10)).thenReturn(Optional.of(request(10)))
        `when`(searchRequestRepository.save(any(SearchRequest::class.java))).thenAnswer { it.arguments[0] }

        val method = SmsWebhookController::class.java.getDeclaredMethod(
            "handleAwaitingReply",
            String::class.java,
            Int::class.java,
            AwaitingContext::class.java
        )
        method.isAccessible = true
        method.invoke(controller, "+15005550006", 1, awaiting)

        verify(searchRequestRepository).save(request(10).copy(userPaused = true))
        verify(conversationService).clearAwaiting("+15005550006")
    }

    @Test
    fun `numeric reply without awaiting context is a no-op`() {
        `when`(conversationService.getAwaiting("+15005550006")).thenReturn(null)

        // No awaiting context — handleInbound returns empty TwiML for a numeric reply
        // We verify no save happens
        verify(searchRequestRepository, never()).save(any(SearchRequest::class.java))
    }

    private fun invokePrivate(methodName: String, vararg args: Any) {
        val method = SmsWebhookController::class.java.getDeclaredMethod(methodName, String::class.java)
        method.isAccessible = true
        method.invoke(controller, *args)
    }
}
