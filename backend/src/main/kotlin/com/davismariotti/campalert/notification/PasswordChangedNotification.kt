package com.davismariotti.campalert.notification

import com.davismariotti.notifications.EmailContent
import com.davismariotti.notifications.Notification

class PasswordChangedNotification(
    private val frontendBaseUrl: String,
) : Notification() {
    override fun email(): EmailContent =
        EmailContent.Templated(
            subject = "Your CampAlert password was changed",
            template = "email/password-changed",
            params = mapOf(
                "frontendBaseUrl" to frontendBaseUrl,
            ),
        )
}
