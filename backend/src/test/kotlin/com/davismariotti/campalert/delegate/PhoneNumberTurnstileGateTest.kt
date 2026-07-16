package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.model.AddPhoneNumberBody
import com.davismariotti.campalert.api.model.ErrorResponse
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.PhoneNumberService
import com.davismariotti.campalert.service.sms.TwilioVerifyService
import com.davismariotti.campalert.service.turnstile.TurnstileService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus

/** Covers the TURNSTILE_FAILED rejection path on addPhoneNumber() — the happy path is exercised elsewhere. */
class PhoneNumberTurnstileGateTest {
    private val phoneNumberRepository = mock(PhoneNumberRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val twilioVerifyService = mock(TwilioVerifyService::class.java)
    private val phoneNumberService = mock(PhoneNumberService::class.java)
    private val turnstileService = mock(TurnstileService::class.java)

    private val delegate = PhoneNumbersDelegateImpl(
        phoneNumberRepository,
        userRepository,
        twilioVerifyService,
        phoneNumberService,
        turnstileService,
    )

    @Test
    fun `addPhoneNumber returns 403 TURNSTILE_FAILED and never triggers a Twilio Verify SMS when verification fails`() {
        `when`(turnstileService.verify(anyString())).thenReturn(false)

        val result = delegate.addPhoneNumber(AddPhoneNumberBody(phone = "+12125551234", smsConsent = true, turnstileToken = "bad"))

        assertEquals(HttpStatus.valueOf(403), result.statusCode)
        assertEquals("TURNSTILE_FAILED", (result.body as ErrorResponse).code)
        verify(twilioVerifyService, never()).startVerification(anyString())
        verify(phoneNumberRepository, never()).save(org.mockito.ArgumentMatchers.any())
    }
}
