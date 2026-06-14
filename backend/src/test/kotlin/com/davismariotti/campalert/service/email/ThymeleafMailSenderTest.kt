package com.davismariotti.campalert.service.email

import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.mail.javamail.JavaMailSender
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.IContext

class ThymeleafMailSenderTest {
    private val javaMailSender = mock(JavaMailSender::class.java)
    private val templateEngine = mock(TemplateEngine::class.java)

    private val sender = ThymeleafMailSender(
        javaMailSender = javaMailSender,
        templateEngine = templateEngine,
        fromAddress = "noreply@campalert.app",
    )

    private fun mimeMessage() =
        MimeMessage(null as Session?).also {
            `when`(javaMailSender.createMimeMessage()).thenReturn(it)
        }

    @Test
    fun `send passes correct template name and variables to TemplateEngine`() {
        `when`(templateEngine.process(eq("email/verify"), any(IContext::class.java))).thenReturn("<html>123456</html>")
        mimeMessage()

        sender.send(
            to = "user@example.com",
            subject = "Verify your email",
            template = "email/verify",
            variables = mapOf("code" to "123456", "verifyUrl" to "http://localhost:5173/verify-email?verificationId=abc", "expiryMinutes" to "10"),
        )

        val contextCaptor = ArgumentCaptor.forClass(IContext::class.java)
        verify(templateEngine).process(eq("email/verify"), contextCaptor.capture())
        assertEquals("123456", contextCaptor.value.getVariable("code"))
        assertEquals("http://localhost:5173/verify-email?verificationId=abc", contextCaptor.value.getVariable("verifyUrl"))
        assertEquals("10", contextCaptor.value.getVariable("expiryMinutes"))
    }

    @Test
    fun `send invokes javaMailSender with the rendered message`() {
        val renderedHtml = "<html><body>Your code: 654321</body></html>"
        `when`(templateEngine.process(eq("email/verify"), any(IContext::class.java))).thenReturn(renderedHtml)
        val msg = mimeMessage()

        sender.send("user@example.com", "Verify", "email/verify", mapOf("code" to "654321"))

        verify(javaMailSender).send(msg)
    }

    @Test
    fun `send sets correct from and to addresses`() {
        `when`(templateEngine.process(anyString(), any(IContext::class.java))).thenReturn("<html></html>")
        val msg = mimeMessage()

        sender.send("camper@example.com", "Verify", "email/verify", emptyMap())

        assertEquals("noreply@campalert.app", msg.from.first().toString())
        assertTrue(msg.allRecipients?.any { it.toString() == "camper@example.com" } == true)
    }

    @Test
    fun `send sets the subject`() {
        `when`(templateEngine.process(anyString(), any(IContext::class.java))).thenReturn("<html></html>")
        val msg = mimeMessage()

        sender.send("u@example.com", "Verify your CampAlert email", "email/verify", emptyMap())

        assertEquals("Verify your CampAlert email", msg.subject)
    }

    @Test
    fun `code is not placed in the subject or recipient address`() {
        val secretCode = "987654"
        `when`(templateEngine.process(anyString(), any(IContext::class.java))).thenReturn("<html>$secretCode</html>")
        val msg = mimeMessage()

        sender.send("user@example.com", "Verify your email", "email/verify", mapOf("code" to secretCode))

        assertFalse(msg.subject.contains(secretCode), "subject must not contain the code")
        assertFalse((msg.allRecipients ?: emptyArray()).joinToString { it.toString() }.contains(secretCode), "recipient must not contain the code")
    }

    @Test
    fun `reset token is not placed in the subject or recipient address`() {
        val rawToken = "SUPERSECRETTOKEN"
        val resetUrl = "http://localhost:5173/reset-password?resetId=xyz&token=$rawToken"
        `when`(templateEngine.process(anyString(), any(IContext::class.java))).thenReturn("<html>$resetUrl</html>")
        val msg = mimeMessage()

        sender.send("user@example.com", "Reset your password", "email/reset-password", mapOf("resetUrl" to resetUrl))

        assertFalse(msg.subject.contains(rawToken), "subject must not contain the raw token")
        assertFalse((msg.allRecipients ?: emptyArray()).joinToString { it.toString() }.contains(rawToken), "recipient must not contain the raw token")
    }

    @Test
    fun `InMemoryMailSender accumulates messages without sending`() {
        val inMemory = InMemoryMailSender()

        inMemory.send("a@example.com", "Subject A", "email/verify", mapOf("code" to "111111"))
        inMemory.send("b@example.com", "Subject B", "email/reset-password", mapOf("resetUrl" to "http://x"))

        assertEquals(2, inMemory.sent.size)
        assertEquals("a@example.com", inMemory.sent[0].to)
        assertEquals("111111", inMemory.sent[0].variables["code"])
        assertEquals("b@example.com", inMemory.sent[1].to)
    }

    @Test
    fun `InMemoryMailSender reset clears sent messages`() {
        val inMemory = InMemoryMailSender()
        inMemory.send("a@example.com", "Subject", "email/verify", emptyMap())
        inMemory.reset()
        assertTrue(inMemory.sent.isEmpty())
    }
}
