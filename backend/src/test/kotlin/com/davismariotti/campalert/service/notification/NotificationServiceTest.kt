package com.davismariotti.campalert.service.notification

import com.davismariotti.campalert.model.PhoneNumber
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.notification.Notification
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.service.email.MailSender
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Instant

class NotificationServiceTest {
    @Suppress("UNCHECKED_CAST")
    private fun <T> anyK(): T = org.mockito.Mockito.any<T>() as T

    private val mailSender = mock(MailSender::class.java)
    private val smsSender = mock(SmsSender::class.java)
    private val pushoverSvc = mock(PushoverNotificationService::class.java)
    private val phoneRepo = mock(PhoneNumberRepository::class.java)

    private val service = NotificationService(
        mailSender = mailSender,
        smsSender = smsSender,
        pushoverNotificationService = pushoverSvc,
        phoneNumberRepository = phoneRepo,
    )

    private val phone = PhoneNumber(
        id = 1L,
        userId = 42L,
        phone = "+15005550006",
        status = PhoneNumberStatus.VERIFIED,
        smsConsentAt = Instant.now(),
    )

    private val smsUser = User(id = 42L, email = "user@example.com", passwordHash = "hash")

    @Test
    fun `send dispatches email when template is present`() {
        val notification = object : Notification(smsUser) {
            override fun getEmailSubject() = "Test Subject"

            override fun getEmailTemplate() = java.util.Optional.of("email/test")

            override fun getEmailParameters() = mapOf("key" to "value")
        }

        service.send(notification)

        verify(mailSender).send("user@example.com", "Test Subject", "email/test", mapOf("key" to "value"))
        verify(smsSender, never()).send(anyK(), anyK())
    }

    @Test
    fun `send dispatches SMS to verified phone when SMS content is present`() {
        `when`(phoneRepo.findByUserIdAndStatus(42L, PhoneNumberStatus.VERIFIED)).thenReturn(listOf(phone))
        val notification = object : Notification(smsUser) {
            override fun getSmsContent() = java.util.Optional.of("Hello from CampAlert")
        }

        service.send(notification)

        verify(smsSender).send("+15005550006", "Hello from CampAlert")
        verify(mailSender, never()).send(anyK(), anyK(), anyK(), anyK())
    }

    @Test
    fun `send skips SMS when no verified phone exists`() {
        `when`(phoneRepo.findByUserIdAndStatus(anyLong(), anyK())).thenReturn(emptyList())
        val notification = object : Notification(smsUser) {
            override fun getSmsContent() = java.util.Optional.of("Hello")
        }

        service.send(notification)

        verify(smsSender, never()).send(anyK(), anyK())
    }

    @Test
    fun `send routes SMS to Pushover when override is enabled`() {
        val pushoverUser = smsUser.copy(
            pushoverOverrideEnabled = true,
            pushoverApiToken = "api-token",
            pushoverUserKey = "user-key",
        )
        val notification = object : Notification(pushoverUser) {
            override fun getSmsContent() = java.util.Optional.of("Alert!")
        }

        service.send(notification)

        verify(pushoverSvc).notify(pushoverUser, "Alert!")
        verify(smsSender, never()).send(anyK(), anyK())
        verify(phoneRepo, never()).findByUserIdAndStatus(anyLong(), anyK())
    }

    @Test
    fun `sendAsync swallows exceptions and does not rethrow`() {
        `when`(mailSender.send(anyK(), anyK(), anyK(), anyK())).thenThrow(RuntimeException("SMTP down"))
        val notification = object : Notification(smsUser) {
            override fun getEmailTemplate() = java.util.Optional.of("email/test")
        }

        service.sendAsync(notification)
    }
}
