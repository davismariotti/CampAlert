package com.davismariotti.campalert.service.email

import com.davismariotti.campalert.config.PasswordResetProperties
import com.davismariotti.campalert.model.PasswordReset
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.notification.Notification
import com.davismariotti.campalert.notification.ResetPasswordNotification
import com.davismariotti.campalert.repository.PasswordResetRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.SessionRevocationService
import com.davismariotti.campalert.service.email.PasswordResetService.ResetResult
import com.davismariotti.campalert.service.notification.NotificationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository
import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.UUID

class PasswordResetServiceTest {
    private val passwordResetRepository = mock(PasswordResetRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val notificationService = mock(NotificationService::class.java)
    private val passwordEncoder = BCryptPasswordEncoder()
    private val rememberMeTokenRepository = mock(PersistentTokenRepository::class.java)
    private val sessionRevocationService = mock(SessionRevocationService::class.java)
    private val props = PasswordResetProperties(
        expiresIn = Duration.ofMinutes(15),
        resendCooldown = Duration.ofSeconds(60),
    )
    private val service = PasswordResetService(
        passwordResetRepository = passwordResetRepository,
        userRepository = userRepository,
        notificationService = notificationService,
        passwordEncoder = passwordEncoder,
        rememberMeTokenRepository = rememberMeTokenRepository,
        sessionRevocationService = sessionRevocationService,
        props = props,
        frontendBaseUrl = "http://localhost:5173",
    )

    private val savedPasswordResets = mutableListOf<PasswordReset>()
    private val savedUsers = mutableListOf<User>()

    @BeforeEach
    fun setUp() {
        savedPasswordResets.clear()
        savedUsers.clear()

        `when`(passwordResetRepository.consumeAllPendingByUserId(anyLong(), anyKt())).thenReturn(0)
        `when`(passwordResetRepository.save(anyKt())).thenAnswer {
            (it.arguments[0] as? PasswordReset)?.also { row -> savedPasswordResets.add(row) }
        }
        `when`(userRepository.save(anyKt())).thenAnswer {
            (it.arguments[0] as? User)?.also { user -> savedUsers.add(user) }
        }
    }

    // ── forgotPassword ────────────────────────────────────────────────────────

    @Test
    fun `forgotPassword issues reset to verified account outside cooldown`() {
        val sent = mutableListOf<Notification>()
        doAnswer {
            sent.add(it.getArgument(0))
            null
        }.`when`(notificationService).sendAsync(anyKt())
        val user = verifiedUser()
        `when`(userRepository.findByEmail("user@example.com")).thenReturn(user)
        `when`(passwordResetRepository.findLatestByUserId(anyLong())).thenReturn(null)

        service.forgotPassword("user@example.com")

        assertEquals(1, sent.size)
        assertEquals("email/reset-password", (sent[0] as ResetPasswordNotification).getEmailTemplate().get())
    }

    @Test
    fun `forgotPassword does nothing for unknown email (enumeration resistance)`() {
        `when`(userRepository.findByEmail("ghost@example.com")).thenReturn(null)

        service.forgotPassword("ghost@example.com")

        verify(passwordResetRepository, never()).save(anyKt())
        verify(notificationService, never()).sendAsync(anyKt())
    }

    @Test
    fun `forgotPassword does nothing for unverified account (enumeration resistance)`() {
        val user = unverifiedUser()
        `when`(userRepository.findByEmail("user@example.com")).thenReturn(user)

        service.forgotPassword("user@example.com")

        verify(passwordResetRepository, never()).save(anyKt())
        verify(notificationService, never()).sendAsync(anyKt())
    }

    @Test
    fun `forgotPassword does nothing within cooldown period`() {
        val user = verifiedUser()
        `when`(userRepository.findByEmail("user@example.com")).thenReturn(user)
        `when`(passwordResetRepository.findLatestByUserId(anyLong())).thenReturn(
            pendingRow(user.id!!, createdSecondsAgo = 30),
        )

        service.forgotPassword("user@example.com")

        verify(notificationService, never()).sendAsync(anyKt())
    }

    @Test
    fun `forgotPassword issues replacement reset after cooldown elapses`() {
        val user = verifiedUser()
        `when`(userRepository.findByEmail("user@example.com")).thenReturn(user)
        `when`(passwordResetRepository.findLatestByUserId(anyLong())).thenReturn(
            pendingRow(user.id!!, createdSecondsAgo = 90),
        )

        service.forgotPassword("user@example.com")

        verify(notificationService).sendAsync(anyKt<ResetPasswordNotification>())
        verify(passwordResetRepository).consumeAllPendingByUserId(anyLong(), anyKt())
    }

    @Test
    fun `forgotPassword issues sendAsync and saves reset row`() {
        val user = verifiedUser()
        `when`(userRepository.findByEmail("user@example.com")).thenReturn(user)
        `when`(passwordResetRepository.findLatestByUserId(anyLong())).thenReturn(null)

        service.forgotPassword("user@example.com")

        verify(notificationService).sendAsync(anyKt<ResetPasswordNotification>())
        assertEquals(1, savedPasswordResets.size)
    }

    // ── issueReset ────────────────────────────────────────────────────────────

    @Test
    fun `issueReset sends ResetPasswordNotification with resetUrl containing resetId and token`() {
        val sent = mutableListOf<Notification>()
        doAnswer {
            sent.add(it.getArgument(0))
            null
        }.`when`(notificationService).sendAsync(anyKt())

        service.issueReset(1L, "user@example.com")

        assertEquals(1, sent.size)
        val resetUrl = (sent[0] as ResetPasswordNotification).getEmailParameters()["resetUrl"] as String
        assertTrue(resetUrl.startsWith("http://localhost:5173/reset-password?resetId="), "resetUrl must include resetId")
        assertTrue(resetUrl.contains("&token="), "resetUrl must include token")
    }

    @Test
    fun `issueReset stores SHA-256 hash of the generated token`() {
        val sent = mutableListOf<Notification>()
        doAnswer {
            sent.add(it.getArgument(0))
            null
        }.`when`(notificationService).sendAsync(anyKt())

        service.issueReset(1L, "user@example.com")

        val resetUrl = (sent[0] as ResetPasswordNotification).getEmailParameters()["resetUrl"] as String
        val token = resetUrl.substringAfter("&token=")
        assertEquals(1, savedPasswordResets.size)
        assertEquals(service.sha256(token), savedPasswordResets[0].tokenHash)
    }

    // ── consumeReset ──────────────────────────────────────────────────────────

    @Test
    fun `consumeReset returns SUCCESS and updates password hash`() {
        val user = verifiedUser()
        val token = rawToken()
        val row = pendingRow(user.id!!, tokenHash = service.sha256(token))
        setupConsumeFlow(row, user)

        val result = service.consumeReset(row.id, token, "newPassword1!")

        assertEquals(ResetResult.SUCCESS, result)
        assertEquals(1, savedUsers.size)
        assertTrue(passwordEncoder.matches("newPassword1!", savedUsers[0].passwordHash))
    }

    @Test
    fun `consumeReset returns INVALID_OR_EXPIRED for unknown resetId`() {
        `when`(passwordResetRepository.findPendingByIdForUpdate(anyKt())).thenReturn(null)

        val result = service.consumeReset(UUID.randomUUID(), rawToken(), "newPassword1!")

        assertEquals(ResetResult.INVALID_OR_EXPIRED, result)
    }

    @Test
    fun `consumeReset returns INVALID_OR_EXPIRED for expired row`() {
        val user = verifiedUser()
        val token = rawToken()
        val row = pendingRow(user.id!!, tokenHash = service.sha256(token), expiresAt = Instant.now().minusSeconds(1))
        setupConsumeFlow(row, user)

        val result = service.consumeReset(row.id, token, "newPassword1!")

        assertEquals(ResetResult.INVALID_OR_EXPIRED, result)
        assertTrue(savedUsers.isEmpty())
    }

    @Test
    fun `consumeReset returns INVALID_OR_EXPIRED for wrong token (no brute-force hinting)`() {
        val user = verifiedUser()
        val correctToken = "a".repeat(64)
        val wrongToken = "b".repeat(64)
        val row = pendingRow(user.id!!, tokenHash = service.sha256(correctToken))
        setupConsumeFlow(row, user)

        val result = service.consumeReset(row.id, wrongToken, "newPassword1!")

        assertEquals(ResetResult.INVALID_OR_EXPIRED, result)
        assertTrue(savedUsers.isEmpty())
    }

    @Test
    fun `consumeReset returns PASSWORD_TOO_WEAK for password shorter than 8 chars`() {
        val user = verifiedUser()
        val token = rawToken()
        val row = pendingRow(user.id!!, tokenHash = service.sha256(token))
        setupConsumeFlow(row, user)

        val result = service.consumeReset(row.id, token, "short")

        assertEquals(ResetResult.PASSWORD_TOO_WEAK, result)
        verify(passwordResetRepository, never()).findPendingByIdForUpdate(anyKt())
    }

    @Test
    fun `consumeReset returns PASSWORD_SAME_AS_CURRENT when new password matches old`() {
        val currentPassword = "currentPassword1!"
        val user = verifiedUser(passwordHash = passwordEncoder.encode(currentPassword)!!)
        val token = rawToken()
        val row = pendingRow(user.id!!, tokenHash = service.sha256(token))
        setupConsumeFlow(row, user)

        val result = service.consumeReset(row.id, token, currentPassword)

        assertEquals(ResetResult.PASSWORD_SAME_AS_CURRENT, result)
        assertTrue(savedUsers.isEmpty())
    }

    @Test
    fun `consumeReset removes remember-me tokens on success`() {
        val user = verifiedUser()
        val token = rawToken()
        val row = pendingRow(user.id!!, tokenHash = service.sha256(token))
        setupConsumeFlow(row, user)

        service.consumeReset(row.id, token, "newPassword1!")

        verify(rememberMeTokenRepository).removeUserTokens(user.email)
    }

    @Test
    fun `consumeReset revokes all sessions on success`() {
        val user = verifiedUser()
        val token = rawToken()
        val row = pendingRow(user.id!!, tokenHash = service.sha256(token))
        setupConsumeFlow(row, user)

        service.consumeReset(row.id, token, "newPassword1!")

        verify(sessionRevocationService).revokeAllSessionsFor(user.email)
    }

    @Test
    fun `consumeReset consumes all other pending resets on success`() {
        val user = verifiedUser()
        val token = rawToken()
        val row = pendingRow(user.id!!, tokenHash = service.sha256(token))
        setupConsumeFlow(row, user)

        service.consumeReset(row.id, token, "newPassword1!")

        verify(passwordResetRepository).consumeAllPendingByUserId(anyLong(), anyKt())
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyKt(): T = org.mockito.ArgumentMatchers.any<T>() as T

    private fun unverifiedUser(id: Long = 1L) =
        User(
            id = id,
            email = "user@example.com",
            passwordHash = passwordEncoder.encode("oldPassword1!")!!,
            emailVerifiedAt = null,
        )

    private fun verifiedUser(id: Long = 1L, passwordHash: String = passwordEncoder.encode("oldPassword1!")!!) =
        User(
            id = id,
            email = "user@example.com",
            passwordHash = passwordHash,
            emailVerifiedAt = Instant.now().minusSeconds(3600),
        )

    private fun pendingRow(
        userId: Long,
        tokenHash: String = service.sha256(rawToken()),
        createdSecondsAgo: Long = 300,
        expiresAt: Instant = Instant.now().plusSeconds(900),
    ) = PasswordReset(
        id = UUID.randomUUID(),
        userId = userId,
        tokenHash = tokenHash,
        createdAt = Instant.now().minusSeconds(createdSecondsAgo),
        expiresAt = expiresAt,
        consumedAt = null,
    )

    private fun setupConsumeFlow(row: PasswordReset, user: User) {
        `when`(passwordResetRepository.findPendingByIdForUpdate(row.id)).thenReturn(row)
        `when`(userRepository.findById(row.userId)).thenReturn(Optional.of(user))
    }

    private fun rawToken(): String = "a".repeat(64)

    private fun assertNotNull(value: Any?) {
        assertTrue(value != null, "Expected non-null value")
    }
}
