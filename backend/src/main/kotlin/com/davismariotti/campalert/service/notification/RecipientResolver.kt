package com.davismariotti.campalert.service.notification

import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.notifications.PushTarget
import com.davismariotti.notifications.PushoverTarget
import com.davismariotti.notifications.Recipient
import com.davismariotti.notifications.SimpleRecipient
import org.springframework.stereotype.Component

/**
 * Resolves a CampAlert [User] into a library [Recipient]. This recipient resolution is deliberately
 * app-specific and stays in CampAlert: it looks up the user's verified phone and, when the Pushover
 * admin override is enabled, supplies a [PushoverTarget] carrying that user's own Pushover
 * credentials. The library itself never sees a `User`.
 */
@Component
class RecipientResolver(
    private val phoneNumberRepository: PhoneNumberRepository,
) {
    fun resolve(user: User): Recipient {
        val phone = phoneNumberRepository
            .findByUserIdAndStatus(user.id!!, PhoneNumberStatus.VERIFIED)
            .firstOrNull()
            ?.phone

        val pushTargets: List<PushTarget> =
            if (user.pushoverOverrideEnabled && user.pushoverApiToken != null && user.pushoverUserKey != null) {
                listOf(PushoverTarget(user.pushoverApiToken!!, user.pushoverUserKey!!))
            } else {
                emptyList()
            }

        return SimpleRecipient(email = user.email, phone = phone, pushTargets = pushTargets)
    }
}
