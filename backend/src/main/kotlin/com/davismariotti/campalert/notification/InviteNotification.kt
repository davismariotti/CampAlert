package com.davismariotti.campalert.notification

import com.davismariotti.notifications.EmailContent
import com.davismariotti.notifications.Notification

class InviteNotification(
    private val inviteUrl: String,
    private val expiryDays: String,
    private val frontendBaseUrl: String,
) : Notification() {
    override fun email(): EmailContent =
        EmailContent.Templated(
            subject = "You're invited to CampAlert",
            template = "email/invite",
            params = mapOf(
                "inviteUrl" to inviteUrl,
                "expiryDays" to expiryDays,
                "frontendBaseUrl" to frontendBaseUrl,
            ),
        )
}
