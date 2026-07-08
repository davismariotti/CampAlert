package com.davismariotti.campalert.notification

import com.davismariotti.notifications.EmailContent
import com.davismariotti.notifications.Notification

class WelcomeNotification(
    private val frontendBaseUrl: String,
) : Notification() {
    override fun email(): EmailContent =
        EmailContent.Templated(
            subject = "Welcome to CampAlert",
            template = "email/welcome",
            params = mapOf("frontendBaseUrl" to frontendBaseUrl),
        )
}
