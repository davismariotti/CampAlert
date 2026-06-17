package com.davismariotti.campalert.notification

import com.davismariotti.campalert.model.User
import java.util.Optional

abstract class Notification(
    val user: User
) {
    open fun getEmailSubject(): String = ""

    open fun getEmailTemplate(): Optional<String> = Optional.empty()

    open fun getEmailParameters(): Map<String, Any> = emptyMap()

    open fun getSmsContent(): Optional<String> = Optional.empty()
}
