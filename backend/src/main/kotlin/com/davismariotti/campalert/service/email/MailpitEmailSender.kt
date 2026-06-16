package com.davismariotti.campalert.service.email

import jakarta.mail.internet.MimeMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["campfinder.email.provider"], havingValue = "mailpit")
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
class MailpitEmailSender(
    private val javaMailSender: JavaMailSender,
    @param:Value($$"${campfinder.email.from-address}") private val fromAddress: String,
) : EmailSender {
    override fun send(to: String, subject: String, htmlBody: String) {
        val message: MimeMessage = javaMailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, true, "UTF-8")
        helper.setFrom(fromAddress)
        helper.setTo(to)
        helper.setSubject(subject)
        helper.setText(htmlBody, true)
        javaMailSender.send(message)
    }
}
