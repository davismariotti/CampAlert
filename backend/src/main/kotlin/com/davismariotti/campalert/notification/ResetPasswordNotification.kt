package com.davismariotti.campalert.notification

import com.davismariotti.campalert.model.User
import java.util.Optional

class ResetPasswordNotification(
    user: User,
    private val resetUrl: String,
    private val expiryMinutes: String,
    private val frontendBaseUrl: String,
) : Notification(user) {
    override fun getEmailSubject() = "Reset your CampAlert password"

    override fun getEmailTemplate(): Optional<String> = Optional.of("email/reset-password")

    override fun getEmailParameters(): Map<String, Any> =
        mapOf(
            "resetUrl" to resetUrl,
            "expiryMinutes" to expiryMinutes,
            "frontendBaseUrl" to frontendBaseUrl,
        )
}
