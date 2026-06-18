package com.davismariotti.campalert.service.notification

import com.davismariotti.campalert.model.User
import net.pushover.client.PushoverMessage
import net.pushover.client.PushoverRestClient
import org.springframework.stereotype.Service

@Service
class PushoverNotificationService(
    private val client: PushoverRestClient,
) {
    fun notify(user: User, message: String) {
        val apiToken = requireNotNull(user.pushoverApiToken) { "User ${user.id} has no Pushover API token" }
        val userKey = requireNotNull(user.pushoverUserKey) { "User ${user.id} has no Pushover user key" }
        client.pushMessage(
            PushoverMessage
                .Builder()
                .setApiToken(apiToken)
                .setUserId(userKey)
                .setMessage(message)
                .build(),
        )
    }
}
