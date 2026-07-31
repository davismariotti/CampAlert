package com.davismariotti.campalert.service.invite

import com.davismariotti.campalert.exception.ConflictException
import com.davismariotti.campalert.model.Invite
import com.davismariotti.campalert.model.InviteRedemption
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.notification.InviteNotification
import com.davismariotti.campalert.repository.InviteRedemptionRepository
import com.davismariotti.campalert.repository.InviteRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.notification.NotificationService
import com.davismariotti.campalert.util.CryptoUtils
import com.davismariotti.notifications.EmailContent
import com.davismariotti.notifications.Notification
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Instant
import java.util.UUID

class InviteServiceTest {
    private val inviteRepository = mock(InviteRepository::class.java)
    private val inviteRedemptionRepository = mock(InviteRedemptionRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val notificationService = mock(NotificationService::class.java)
    private val service = InviteService(
        inviteRepository = inviteRepository,
        inviteRedemptionRepository = inviteRedemptionRepository,
        userRepository = userRepository,
        notificationService = notificationService,
        frontendBaseUrl = "http://localhost:5173",
    )

    private val savedInvites = mutableListOf<Invite>()

    @BeforeEach
    fun setUp() {
        savedInvites.clear()
        `when`(inviteRepository.save(anyKt())).thenAnswer {
            (it.arguments[0] as Invite).also { row -> savedInvites.add(row) }
        }
    }

    // ── createEmailInvites ───────────────────────────────────────────────────

    @Test
    fun `createEmailInvites creates an invite and sends a notification for a new address`() {
        `when`(userRepository.findByEmail("new@example.com")).thenReturn(null)
        val sent = mutableListOf<Notification>()
        doAnswer {
            sent.add(it.getArgument(0))
            null
        }.`when`(notificationService).sendAsync(anyKt(), anyKt())

        val results = service.createEmailInvites(listOf("new@example.com"), expiresInDays = null, actingAdminId = 1L)

        assertEquals(1, results.size)
        assertTrue(results[0].created)
        assertEquals(1, savedInvites.size)
        assertEquals("new@example.com", savedInvites[0].email)
        assertEquals(1, savedInvites[0].maxUses)
        assertEquals(1, sent.size)
        val url = ((sent[0] as InviteNotification).email() as EmailContent.Templated).params["inviteUrl"] as String
        assertTrue(url.contains("inviteId=${savedInvites[0].id}"), "invite URL must include the invite id")
    }

    @Test
    fun `createEmailInvites skips an address that is already registered`() {
        `when`(userRepository.findByEmail("taken@example.com")).thenReturn(User(id = 5L, email = "taken@example.com", passwordHash = "x"))

        val results = service.createEmailInvites(listOf("taken@example.com"), expiresInDays = null, actingAdminId = 1L)

        assertEquals(1, results.size)
        assertFalse(results[0].created)
        assertEquals("ALREADY_REGISTERED", results[0].reason)
        assertTrue(savedInvites.isEmpty())
        verify(notificationService, never()).sendAsync(anyKt(), anyKt())
    }

    @Test
    fun `createEmailInvites handles a mixed batch independently per address`() {
        `when`(userRepository.findByEmail("taken@example.com")).thenReturn(User(id = 5L, email = "taken@example.com", passwordHash = "x"))
        `when`(userRepository.findByEmail("new@example.com")).thenReturn(null)

        val results = service.createEmailInvites(listOf("taken@example.com", "new@example.com"), expiresInDays = null, actingAdminId = 1L)

        assertEquals(false, results.first { it.email == "taken@example.com" }.created)
        assertEquals(true, results.first { it.email == "new@example.com" }.created)
        assertEquals(1, savedInvites.size)
    }

    @Test
    fun `createEmailInvites stores the SHA-256 hash of the generated token, not the token itself`() {
        `when`(userRepository.findByEmail(anyKt())).thenReturn(null)
        val sent = mutableListOf<Notification>()
        doAnswer {
            sent.add(it.getArgument(0))
            null
        }.`when`(notificationService).sendAsync(anyKt(), anyKt())

        service.createEmailInvites(listOf("new@example.com"), expiresInDays = null, actingAdminId = 1L)

        val url = ((sent[0] as InviteNotification).email() as EmailContent.Templated).params["inviteUrl"] as String
        val token = url.substringAfter("&token=").substringBefore("&")
        assertEquals(CryptoUtils.sha256(token), savedInvites[0].tokenHash)
        assertFalse(savedInvites[0].tokenHash.contains(token))
    }

    @Test
    fun `createEmailInvites defaults expiry to 7 days`() {
        `when`(userRepository.findByEmail(anyKt())).thenReturn(null)

        service.createEmailInvites(listOf("new@example.com"), expiresInDays = null, actingAdminId = 1L)

        val invite = savedInvites[0]
        val expectedExpiry = invite.createdAt.plusSeconds(7 * 24 * 3600)
        assertTrue(invite.expiresAt.epochSecond in (expectedExpiry.epochSecond - 5)..(expectedExpiry.epochSecond + 5))
    }

    @Test
    fun `createEmailInvites honors a custom expiry`() {
        `when`(userRepository.findByEmail(anyKt())).thenReturn(null)

        service.createEmailInvites(listOf("new@example.com"), expiresInDays = 1L, actingAdminId = 1L)

        val invite = savedInvites[0]
        val expectedExpiry = invite.createdAt.plusSeconds(24 * 3600)
        assertTrue(invite.expiresAt.epochSecond in (expectedExpiry.epochSecond - 5)..(expectedExpiry.epochSecond + 5))
    }

    // ── createPublicLink ─────────────────────────────────────────────────────

    @Test
    fun `createPublicLink creates an invite with no email and the given capacity`() {
        val (invite, token) = service.createPublicLink(maxUses = 100, expiresInDays = null, actingAdminId = 1L)

        assertEquals(null, invite.email)
        assertEquals(100, invite.maxUses)
        assertEquals(CryptoUtils.sha256(token), invite.tokenHash)
    }

    // ── deactivateInvite ─────────────────────────────────────────────────────

    @Test
    fun `deactivateInvite sets deactivatedAt on an active invite`() {
        val invite = activeInvite()
        `when`(inviteRepository.findByIdForUpdate(invite.id)).thenReturn(invite)

        val result = service.deactivateInvite(invite.id)

        assertEquals(invite.id, result.id)
        assertTrue(result.deactivatedAt != null)
    }

    @Test
    fun `deactivateInvite throws ConflictException when already deactivated`() {
        val invite = activeInvite().copy(deactivatedAt = Instant.now())
        `when`(inviteRepository.findByIdForUpdate(invite.id)).thenReturn(invite)

        assertThrows(ConflictException::class.java) { service.deactivateInvite(invite.id) }
    }

    // ── validateAndLock ──────────────────────────────────────────────────────

    @Test
    fun `validateAndLock returns the invite for a valid unexpired token`() {
        val token = "a".repeat(64)
        val invite = activeInvite(tokenHash = CryptoUtils.sha256(token), email = "invited@example.com")
        `when`(inviteRepository.findByIdForUpdate(invite.id)).thenReturn(invite)

        val result = service.validateAndLock(invite.id, token, "invited@example.com")

        assertEquals(invite.id, result.id)
    }

    @Test
    fun `validateAndLock throws InvalidOrExpired for an unknown invite id`() {
        `when`(inviteRepository.findByIdForUpdate(anyKt())).thenReturn(null)

        assertThrows(InviteException.InvalidOrExpired::class.java) {
            service.validateAndLock(UUID.randomUUID(), "a".repeat(64), "user@example.com")
        }
    }

    @Test
    fun `validateAndLock throws InvalidOrExpired for a wrong token`() {
        val invite = activeInvite(tokenHash = CryptoUtils.sha256("correct".repeat(9)))
        `when`(inviteRepository.findByIdForUpdate(invite.id)).thenReturn(invite)

        assertThrows(InviteException.InvalidOrExpired::class.java) {
            service.validateAndLock(invite.id, "wrong".repeat(13), "user@example.com")
        }
    }

    @Test
    fun `validateAndLock throws Deactivated for a deactivated invite`() {
        val token = "a".repeat(64)
        val invite = activeInvite(tokenHash = CryptoUtils.sha256(token)).copy(deactivatedAt = Instant.now())
        `when`(inviteRepository.findByIdForUpdate(invite.id)).thenReturn(invite)

        assertThrows(InviteException.Deactivated::class.java) {
            service.validateAndLock(invite.id, token, "user@example.com")
        }
    }

    @Test
    fun `validateAndLock throws Exhausted when usedCount has reached maxUses`() {
        val token = "a".repeat(64)
        val invite = activeInvite(tokenHash = CryptoUtils.sha256(token), maxUses = 1).copy(usedCount = 1)
        `when`(inviteRepository.findByIdForUpdate(invite.id)).thenReturn(invite)

        assertThrows(InviteException.Exhausted::class.java) {
            service.validateAndLock(invite.id, token, "user@example.com")
        }
    }

    @Test
    fun `validateAndLock throws InvalidOrExpired for an expired invite`() {
        val token = "a".repeat(64)
        val invite = activeInvite(tokenHash = CryptoUtils.sha256(token), expiresAt = Instant.now().minusSeconds(1))
        `when`(inviteRepository.findByIdForUpdate(invite.id)).thenReturn(invite)

        assertThrows(InviteException.InvalidOrExpired::class.java) {
            service.validateAndLock(invite.id, token, "user@example.com")
        }
    }

    @Test
    fun `validateAndLock throws EmailMismatch when the submitted email differs from an email-tied invite`() {
        val token = "a".repeat(64)
        val invite = activeInvite(tokenHash = CryptoUtils.sha256(token), email = "invited@example.com")
        `when`(inviteRepository.findByIdForUpdate(invite.id)).thenReturn(invite)

        assertThrows(InviteException.EmailMismatch::class.java) {
            service.validateAndLock(invite.id, token, "someone-else@example.com")
        }
    }

    @Test
    fun `validateAndLock allows a public link to be redeemed with any email`() {
        val token = "a".repeat(64)
        val invite = activeInvite(tokenHash = CryptoUtils.sha256(token), email = null)
        `when`(inviteRepository.findByIdForUpdate(invite.id)).thenReturn(invite)

        val result = service.validateAndLock(invite.id, token, "anyone@example.com")

        assertEquals(invite.id, result.id)
    }

    // ── recordRedemption ─────────────────────────────────────────────────────

    @Test
    fun `recordRedemption saves a redemption row and increments usedCount`() {
        val invite = activeInvite()
        val savedRedemptions = mutableListOf<InviteRedemption>()
        `when`(inviteRedemptionRepository.save(anyKt())).thenAnswer {
            (it.arguments[0] as InviteRedemption).also { row -> savedRedemptions.add(row) }
        }

        service.recordRedemption(invite, userId = 42L)

        assertEquals(1, savedRedemptions.size)
        assertEquals(42L, savedRedemptions[0].userId)
        assertEquals(invite.id, savedRedemptions[0].inviteId)
        assertEquals(1, savedInvites[0].usedCount)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyKt(): T = org.mockito.ArgumentMatchers.any<T>() as T

    private fun activeInvite(
        email: String? = null,
        tokenHash: String = CryptoUtils.sha256("a".repeat(64)),
        maxUses: Int = 1,
        expiresAt: Instant = Instant.now().plusSeconds(3600),
    ) = Invite(
        id = UUID.randomUUID(),
        email = email,
        tokenHash = tokenHash,
        maxUses = maxUses,
        usedCount = 0,
        createdByUserId = 1L,
        createdAt = Instant.now(),
        expiresAt = expiresAt,
    )
}
