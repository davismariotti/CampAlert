package com.davismariotti.campalert.service.sms

import com.davismariotti.campalert.model.PhoneNumber
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.service.PhoneNumberService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Instant
import java.time.LocalDate
import java.util.Optional

class SmsWebhookControllerTest {
    private val phoneNumberRepository = mock(PhoneNumberRepository::class.java)
    private val phoneNumberService = mock(PhoneNumberService::class.java)
    private val searchRequestRepository = mock(SearchRequestRepository::class.java)
    private val conversationService = mock(SmsConversationService::class.java)

    private val service = SmsWebhookService(
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

    private fun request(id: Long, campgroundName: String = "Upper Pines") =
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

    @Test
    fun `handleStop sets phone to OPTED_OUT and pauses requests`() {
        `when`(phoneNumberRepository.findByPhone("+15005550006")).thenReturn(verifiedPhone)
        `when`(phoneNumberRepository.save(any(PhoneNumber::class.java))).thenAnswer { it.arguments[0] }

        service.handleStop("+15005550006")

        verify(phoneNumberRepository).save(verifiedPhone.copy(status = PhoneNumberStatus.OPTED_OUT))
        verify(phoneNumberService).pauseRequestsIfNoVerifiedPhone(42L)
    }

    @Test
    fun `handleStart sets OPTED_OUT phone to VERIFIED and resumes requests`() {
        val optedOut = verifiedPhone.copy(status = PhoneNumberStatus.OPTED_OUT)
        `when`(phoneNumberRepository.findByPhone("+15005550006")).thenReturn(optedOut)
        `when`(phoneNumberRepository.save(any(PhoneNumber::class.java))).thenAnswer { it.arguments[0] }

        service.handleStart("+15005550006")

        verify(phoneNumberRepository).save(optedOut.copy(status = PhoneNumberStatus.VERIFIED))
        verify(phoneNumberService).resumeRequestsIfVerifiedPhone(42L)
    }

    @Test
    fun `handleStart on VERIFIED phone takes no action`() {
        `when`(phoneNumberRepository.findByPhone("+15005550006")).thenReturn(verifiedPhone)

        service.handleStart("+15005550006")

        verify(phoneNumberRepository, never()).save(any(PhoneNumber::class.java))
    }

    @Test
    fun `handleHelp returns TwiML with brand name and STOP instruction`() {
        val twiml = service.handleHelp()
        assertTrue(twiml.contains("CampAlert"))
        assertTrue(twiml.contains("STOP"))
    }

    @Test
    fun `PAUSE with single request in context pauses it and confirms`() {
        `when`(conversationService.getContext("+15005550006")).thenReturn(listOf(10L))
        `when`(searchRequestRepository.findById(10L)).thenReturn(Optional.of(request(10L)))
        `when`(searchRequestRepository.save(any(SearchRequest::class.java))).thenAnswer { it.arguments[0] }

        val twiml = service.handlePause("+15005550006")

        verify(searchRequestRepository).save(request(10L).copy(userPaused = true))
        assertTrue(twiml.contains("paused"))
    }

    @Test
    fun `PAUSE with multiple requests in context sends disambiguation and sets awaiting`() {
        val req1 = request(10L, "Upper Pines")
        val req2 = request(11L, "Yosemite Valley")
        `when`(conversationService.getContext("+15005550006")).thenReturn(listOf(10L, 11L))
        `when`(searchRequestRepository.findById(10L)).thenReturn(Optional.of(req1))
        `when`(searchRequestRepository.findById(11L)).thenReturn(Optional.of(req2))

        val twiml = service.handlePause("+15005550006")

        verify(conversationService).setAwaiting("+15005550006", "PAUSE", listOf(10L, 11L))
        assertTrue(twiml.contains("1."))
        assertTrue(twiml.contains("2."))
        verify(searchRequestRepository, never()).save(any(SearchRequest::class.java))
    }

    @Test
    fun `numeric reply with awaiting context executes PAUSE`() {
        val awaiting = AwaitingContext(intent = "PAUSE", requestIds = listOf(10L, 11L))
        `when`(searchRequestRepository.findById(10L)).thenReturn(Optional.of(request(10L)))
        `when`(searchRequestRepository.save(any(SearchRequest::class.java))).thenAnswer { it.arguments[0] }

        service.handleAwaitingReply("+15005550006", 1, awaiting)

        verify(searchRequestRepository).save(request(10L).copy(userPaused = true))
        verify(conversationService).clearAwaiting("+15005550006")
    }

    @Test
    fun `numeric reply without awaiting context is a no-op`() {
        `when`(conversationService.getAwaiting("+15005550006")).thenReturn(null)

        verify(searchRequestRepository, never()).save(any(SearchRequest::class.java))
    }
}
