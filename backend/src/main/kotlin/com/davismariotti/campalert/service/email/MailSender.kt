package com.davismariotti.campalert.service.email

interface MailSender {
    fun send(
        to: String,
        subject: String,
        template: String,
        variables: Map<String, Any> = emptyMap()
    )
}
