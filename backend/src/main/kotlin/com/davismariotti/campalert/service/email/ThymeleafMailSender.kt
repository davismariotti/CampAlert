package com.davismariotti.campalert.service.email

import jakarta.mail.internet.MimeMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context

@Component
class ThymeleafMailSender(
    private val javaMailSender: JavaMailSender,
    private val templateEngine: TemplateEngine,
    @param:Value($$"${campfinder.email.from-address}") private val fromAddress: String,
) : MailSender {
    override fun send(
        to: String,
        subject: String,
        template: String,
        variables: Map<String, Any>
    ) {
        val context = Context()
        context.setVariables(variables)
        val html = templateEngine.process(template, context)

        val message: MimeMessage = javaMailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, true, "UTF-8")
        helper.setFrom(fromAddress)
        helper.setTo(to)
        helper.setSubject(subject)
        helper.setText(html, true)
        javaMailSender.send(message)
    }
}
