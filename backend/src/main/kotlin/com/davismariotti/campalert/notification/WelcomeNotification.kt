package com.davismariotti.campalert.notification

import com.davismariotti.campalert.model.User
import java.util.Optional

class WelcomeNotification(
    user: User,
    private val frontendBaseUrl: String,
) : Notification(user) {
    override fun getEmailSubject() = "Welcome to CampAlert"

    override fun getEmailTemplate(): Optional<String> = Optional.of("email/welcome")

    override fun getEmailParameters(): Map<String, Any> = mapOf("frontendBaseUrl" to frontendBaseUrl)
}
