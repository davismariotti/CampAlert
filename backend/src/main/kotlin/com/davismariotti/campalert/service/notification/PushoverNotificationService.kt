package com.davismariotti.campalert.service.notification

import com.davismariotti.campalert.model.User
import net.pushover.client.PushoverMessage
import net.pushover.client.PushoverRestClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PushoverNotificationService(
    private val client: PushoverRestClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun notify(user: User, message: String) {
        val apiToken = requireNotNull(user.pushoverApiToken) { "User ${user.id} has no Pushover API token" }
        val userKey = requireNotNull(user.pushoverUserKey) { "User ${user.id} has no Pushover user key" }
        try {
            client.pushMessage(
                PushoverMessage
                    .Builder()
                    .setApiToken(apiToken)
                    .setUserId(userKey)
                    .setMessage(message)
                    .build(),
            )
        } catch (e: Exception) {
            log.warn("Pushover notify failed for userId={}", user.id, e)
            throw e
        }
    }
}
