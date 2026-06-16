package com.davismariotti.campalert.service.email

class InMemoryEmailSender : EmailSender {
    data class SentMessage(
        val to: String,
        val subject: String,
        val htmlBody: String,
    )

    val sent = mutableListOf<SentMessage>()

    override fun send(to: String, subject: String, htmlBody: String) {
        sent.add(SentMessage(to, subject, htmlBody))
    }

    fun reset() = sent.clear()
}
