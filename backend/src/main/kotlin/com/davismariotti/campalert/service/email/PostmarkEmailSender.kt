package com.davismariotti.campalert.service.email

import com.newrelic.api.agent.NewRelic
import com.postmarkapp.postmark.Postmark
import com.postmarkapp.postmark.client.ApiClient
import com.postmarkapp.postmark.client.data.model.message.Message
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["campfinder.email.provider"], havingValue = "postmark")
class PostmarkEmailSender(
    @Value($$"${campfinder.email.postmark.server-token}") serverToken: String,
    @param:Value($$"${campfinder.email.from-address}") private val fromAddress: String,
) : EmailSender {
    private val log = LoggerFactory.getLogger(javaClass)
    private val client: ApiClient = Postmark.getApiClient(serverToken)

    override fun send(to: String, subject: String, htmlBody: String) {
        val start = System.currentTimeMillis()
        try {
            val message = Message(fromAddress, to, subject, htmlBody)
            client.deliverMessage(message)
        } catch (e: Exception) {
            log.warn("Postmark email send failed to={}", to, e)
            throw e
        } finally {
            NewRelic.recordResponseTimeMetric("Custom/Postmark/EmailSend", System.currentTimeMillis() - start)
        }
    }
}
