package com.davismariotti.campalert.notification

import com.davismariotti.notifications.EmailContent
import com.davismariotti.notifications.Notification

class VerifyEmailNotification(
    private val code: String,
    private val verifyUrl: String,
    private val expiryMinutes: String,
    private val frontendBaseUrl: String,
) : Notification() {
    override fun email(): EmailContent =
        EmailContent.Templated(
            subject = "Verify your CampAlert email",
            template = "email/verify",
            params = mapOf(
                "code" to code,
                "verifyUrl" to verifyUrl,
                "expiryMinutes" to expiryMinutes,
                "frontendBaseUrl" to frontendBaseUrl,
            ),
        )
}
