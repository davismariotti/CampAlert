package com.davismariotti.campalert.notification

import com.davismariotti.notifications.EmailContent
import com.davismariotti.notifications.Notification

class ResetPasswordNotification(
    private val resetUrl: String,
    private val expiryMinutes: String,
    private val frontendBaseUrl: String,
) : Notification() {
    override fun email(): EmailContent =
        EmailContent.Templated(
            subject = "Reset your CampAlert password",
            template = "email/reset-password",
            params = mapOf(
                "resetUrl" to resetUrl,
                "expiryMinutes" to expiryMinutes,
                "frontendBaseUrl" to frontendBaseUrl,
            ),
        )
}
