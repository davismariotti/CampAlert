package com.davismariotti.campalert.service.notification

import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.notification.Notification
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.service.email.MailSender
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class NotificationService(
    private val mailSender: MailSender,
    private val smsSender: SmsSender,
    private val pushoverNotificationService: PushoverNotificationService,
    private val phoneNumberRepository: PhoneNumberRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    fun sendAsync(notification: Notification) {
        try {
            send(notification)
        } catch (e: Exception) {
            log.warn("Notification delivery failed for {}", notification::class.simpleName, e)
        }
    }

    fun send(notification: Notification) {
        notification.getEmailTemplate().ifPresent { template ->
            mailSender.send(
                notification.user.email,
                notification.getEmailSubject(),
                template,
                notification.getEmailParameters(),
            )
        }
        notification.getSmsContent().ifPresent { content ->
            dispatchSms(notification.user, content)
        }
    }

    private fun dispatchSms(user: User, content: String) {
        if (user.pushoverOverrideEnabled && user.pushoverApiToken != null && user.pushoverUserKey != null) {
            pushoverNotificationService.notify(user, content)
            return
        }
        val phone = phoneNumberRepository.findByUserIdAndStatus(user.id!!, PhoneNumberStatus.VERIFIED).firstOrNull()
        if (phone == null) {
            log.warn("No verified phone for userId={}, skipping SMS", user.id)
            return
        }
        smsSender.send(phone.phone, content)
    }
}
