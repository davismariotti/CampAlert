package com.davismariotti.campalert.service.email

import org.springframework.stereotype.Component
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context

@Component
class TemplatedMailSender(
    private val emailSender: EmailSender,
    private val templateEngine: TemplateEngine,
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
        emailSender.send(to, subject, html)
    }
}
