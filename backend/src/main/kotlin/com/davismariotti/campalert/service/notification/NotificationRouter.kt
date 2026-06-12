package com.davismariotti.campalert.service.notification

import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.service.sms.SmsNotificationService
import org.springframework.stereotype.Component

@Component
class NotificationRouter(
    private val smsNotificationService: SmsNotificationService,
    private val pushoverNotificationService: PushoverNotificationService,
) {
    fun resolve(user: User): NotificationService {
        if (user.pushoverOverrideEnabled &&
            user.pushoverApiToken != null &&
            user.pushoverUserKey != null
        ) {
            return pushoverNotificationService
        }
        return smsNotificationService
    }
}
