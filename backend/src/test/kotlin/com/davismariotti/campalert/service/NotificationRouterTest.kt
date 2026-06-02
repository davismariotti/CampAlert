package com.davismariotti.campalert.service

import com.davismariotti.campalert.model.User
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class NotificationRouterTest {
    private val smsService = mock(SmsNotificationService::class.java)
    private val router = NotificationRouter(smsService)

    private fun user(pushoverEnabled: Boolean, apiToken: String? = null, userKey: String? = null,) =
        User(
            id = 1L,
            email = "test@example.com",
            passwordHash = "hash",
            pushoverOverrideEnabled = pushoverEnabled,
            pushoverApiToken = apiToken,
            pushoverUserKey = userKey,
        )

    @Test
    fun `resolves SmsNotificationService for standard user`() {
        val result = router.resolve(user(pushoverEnabled = false))
        assertInstanceOf(SmsNotificationService::class.java, result)
    }

    @Test
    fun `resolves SmsNotificationService when override enabled but keys missing`() {
        val result = router.resolve(user(pushoverEnabled = true, apiToken = null, userKey = null))
        assertInstanceOf(SmsNotificationService::class.java, result)
    }

    @Test
    fun `resolves PushoverNotificationService when override enabled with both keys`() {
        val result = router.resolve(user(pushoverEnabled = true, apiToken = "token", userKey = "key"))
        assertInstanceOf(PushoverNotificationService::class.java, result)
    }
}
