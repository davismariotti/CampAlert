package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.model.PhoneNumber
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.service.PhoneNumberService
import com.davismariotti.campalert.service.TwilioConfiguration
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

class SmsWebhookControllerTest {
    private val twilioConfig = mock(TwilioConfiguration::class.java)
    private val phoneNumberRepository = mock(PhoneNumberRepository::class.java)
    private val phoneNumberService = mock(PhoneNumberService::class.java)
    private val controller = SmsWebhookController(twilioConfig, phoneNumberRepository, phoneNumberService)

    private val verifiedPhone =
        PhoneNumber(
            id = 1L,
            userId = 42L,
            phone = "+15005550006",
            status = PhoneNumberStatus.VERIFIED,
            smsConsentAt = Instant.now(),
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

    private fun invokePrivate(methodName: String, vararg args: Any) {
        val method = SmsWebhookController::class.java.getDeclaredMethod(methodName, String::class.java)
        method.isAccessible = true
        method.invoke(controller, *args)
    }
}
