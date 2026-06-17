package com.davismariotti.campalert.notification

import com.davismariotti.campalert.model.User
import java.util.Optional

class VerifyEmailNotification(
    user: User,
    private val code: String,
    private val verifyUrl: String,
    private val expiryMinutes: String,
    private val frontendBaseUrl: String,
) : Notification(user) {
    override fun getEmailSubject() = "Verify your CampAlert email"

    override fun getEmailTemplate(): Optional<String> = Optional.of("email/verify")

    override fun getEmailParameters(): Map<String, Any> =
        mapOf(
            "code" to code,
            "verifyUrl" to verifyUrl,
            "expiryMinutes" to expiryMinutes,
            "frontendBaseUrl" to frontendBaseUrl,
        )
}
