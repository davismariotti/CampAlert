package com.davismariotti.campalert.service.notification

import com.davismariotti.notifications.Channel
import com.davismariotti.notifications.DispatchResult
import com.davismariotti.notifications.EmailContent
import com.davismariotti.notifications.Notification
import com.davismariotti.notifications.NotificationDispatcher
import com.davismariotti.notifications.Recipient
import com.davismariotti.notifications.SendResult
import com.davismariotti.notifications.SimpleRecipient
import com.davismariotti.notifications.SmsContent
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals

class NotificationServiceTest {
    private val dispatcher = mock(NotificationDispatcher::class.java)
    private val service = NotificationService(dispatcher)

    private val recipient: Recipient = SimpleRecipient(email = "user@example.com", phone = "+15005550006")

    @Test
    fun `send delegates to the dispatcher and returns its result`() {
        val notification = object : Notification() {
            override fun email() = EmailContent.Html("Subject", "<p>hi</p>")
        }
        val expected = DispatchResult(mapOf(Channel.EMAIL to SendResult.success()))
        `when`(dispatcher.send(notification, recipient)).thenReturn(expected)

        val result = service.send(notification, recipient)

        assertEquals(expected, result)
        verify(dispatcher).send(notification, recipient)
    }

    @Test
    fun `send logs but does not throw when a channel fails`() {
        val notification = object : Notification() {
            override fun sms() = SmsContent("Hello from CampAlert")
        }
        val failure = DispatchResult(mapOf(Channel.SMS to SendResult.failure(RuntimeException("down"), retryable = true)))
        `when`(dispatcher.send(notification, recipient)).thenReturn(failure)

        val result = service.send(notification, recipient)

        assertEquals(failure, result)
    }

    @Test
    fun `sendAsync delegates to the dispatcher`() {
        val notification = object : Notification() {
            override fun email() = EmailContent.Html("Subject", "<p>hi</p>")
        }
        `when`(dispatcher.send(notification, recipient)).thenReturn(DispatchResult(emptyMap()))

        service.sendAsync(notification, recipient)

        verify(dispatcher).send(notification, recipient)
    }
}
