package com.davismariotti.campalert.service.email

import com.postmarkapp.postmark.Postmark
import com.postmarkapp.postmark.client.ApiClient
import com.postmarkapp.postmark.client.data.model.message.Message
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["campfinder.email.provider"], havingValue = "postmark")
class PostmarkEmailSender(
    @Value($$"${campfinder.email.postmark.server-token}") serverToken: String,
    @param:Value($$"${campfinder.email.from-address}") private val fromAddress: String,
) : EmailSender {
    private val client: ApiClient = Postmark.getApiClient(serverToken)

    override fun send(to: String, subject: String, htmlBody: String) {
        val message = Message(fromAddress, to, subject, htmlBody)
        client.deliverMessage(message)
    }
}
