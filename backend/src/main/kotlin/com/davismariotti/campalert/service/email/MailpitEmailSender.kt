package com.davismariotti.campalert.service.email

import com.davismariotti.notifications.EmailSender
import com.davismariotti.notifications.SendResult
import jakarta.mail.internet.MimeMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component

/**
 * Local-dev email sender backed by Mailpit's SMTP catch-all. Implements the library's [EmailSender]
 * so it slots into [com.davismariotti.notifications.NotificationDispatcher] the same way the
 * library's Postmark sender does; gated on `notifications.email.provider=mailpit`, aliased in
 * `application.properties` to the existing `campfinder.email.provider` value.
 */
@Component
@ConditionalOnProperty(name = ["notifications.email.provider"], havingValue = "mailpit")
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
class MailpitEmailSender(
    private val javaMailSender: JavaMailSender,
    @param:Value($$"${notifications.email.from-address}") private val fromAddress: String,
) : EmailSender {
    override fun send(to: String, subject: String, htmlBody: String): SendResult =
        try {
            val message: MimeMessage = javaMailSender.createMimeMessage()
            val helper = MimeMessageHelper(message, true, "UTF-8")
            helper.setFrom(fromAddress)
            helper.setTo(to)
            helper.setSubject(subject)
            helper.setText(htmlBody, true)
            javaMailSender.send(message)
            SendResult.success()
        } catch (e: Exception) {
            SendResult.failure(e, retryable = true)
        }
}
