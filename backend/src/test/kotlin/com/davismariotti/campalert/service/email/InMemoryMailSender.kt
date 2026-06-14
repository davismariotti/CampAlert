package com.davismariotti.campalert.service.email

class InMemoryMailSender : MailSender {
    data class SentMessage(
        val to: String,
        val subject: String,
        val template: String,
        val variables: Map<String, Any>,
    )

    val sent = mutableListOf<SentMessage>()

    override fun send(
        to: String,
        subject: String,
        template: String,
        variables: Map<String, Any>
    ) {
        sent.add(SentMessage(to, subject, template, variables))
    }

    fun reset() = sent.clear()
}
