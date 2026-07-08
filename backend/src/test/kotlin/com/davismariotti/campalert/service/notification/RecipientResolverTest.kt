package com.davismariotti.campalert.service.notification

import com.davismariotti.campalert.model.PhoneNumber
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.notifications.PushoverTarget
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecipientResolverTest {
    private val phoneRepo = mock(PhoneNumberRepository::class.java)
    private val resolver = RecipientResolver(phoneRepo)

    private val user = User(id = 42L, email = "user@example.com", passwordHash = "hash")
    private val phone = PhoneNumber(
        id = 1L,
        userId = 42L,
        phone = "+15005550006",
        status = PhoneNumberStatus.VERIFIED,
        smsConsentAt = Instant.now(),
    )

    @Test
    fun `resolves email and verified phone`() {
        `when`(phoneRepo.findByUserIdAndStatus(42L, PhoneNumberStatus.VERIFIED)).thenReturn(listOf(phone))

        val recipient = resolver.resolve(user)

        assertEquals("user@example.com", recipient.email)
        assertEquals("+15005550006", recipient.phone)
        assertTrue(recipient.pushTargets.isEmpty())
    }

    @Test
    fun `no verified phone yields a null phone`() {
        `when`(phoneRepo.findByUserIdAndStatus(42L, PhoneNumberStatus.VERIFIED)).thenReturn(emptyList())

        val recipient = resolver.resolve(user)

        assertNull(recipient.phone)
    }

    @Test
    fun `pushover override yields a pushover target with the user's credentials`() {
        `when`(phoneRepo.findByUserIdAndStatus(42L, PhoneNumberStatus.VERIFIED)).thenReturn(emptyList())
        val pushoverUser = user.copy(
            pushoverOverrideEnabled = true,
            pushoverApiToken = "api-token",
            pushoverUserKey = "user-key",
        )

        val recipient = resolver.resolve(pushoverUser)

        assertEquals(listOf(PushoverTarget("api-token", "user-key")), recipient.pushTargets)
    }

    @Test
    fun `override disabled yields no push targets`() {
        `when`(phoneRepo.findByUserIdAndStatus(42L, PhoneNumberStatus.VERIFIED)).thenReturn(emptyList())
        val partial = user.copy(pushoverOverrideEnabled = false, pushoverApiToken = "api-token", pushoverUserKey = "user-key")

        val recipient = resolver.resolve(partial)

        assertTrue(recipient.pushTargets.isEmpty())
    }
}
