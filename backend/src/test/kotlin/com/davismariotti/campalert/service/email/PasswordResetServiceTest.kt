package com.davismariotti.campalert.service.email

import com.davismariotti.campalert.config.PasswordResetProperties
import com.davismariotti.campalert.model.PasswordReset
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.repository.PasswordResetRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.SessionRevocationService
import com.davismariotti.campalert.service.email.PasswordResetService.ResetResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
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
    private val mailSender = InMemoryMailSender()
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
        mailSender = mailSender,
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
        mailSender.reset()
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
        val user = verifiedUser()
        `when`(userRepository.findByEmail("user@example.com")).thenReturn(user)
        `when`(passwordResetRepository.findLatestByUserId(anyLong())).thenReturn(null)

        service.forgotPassword("user@example.com")

        assertEquals(1, mailSender.sent.size)
        assertEquals("email/reset-password", mailSender.sent[0].template)
    }

    @Test
    fun `forgotPassword does nothing for unknown email (enumeration resistance)`() {
        `when`(userRepository.findByEmail("ghost@example.com")).thenReturn(null)

        service.forgotPassword("ghost@example.com")

        assertEquals(0, mailSender.sent.size)
        verify(passwordResetRepository, never()).save(anyKt())
    }

    @Test
    fun `forgotPassword does nothing for unverified account (enumeration resistance)`() {
        val user = unverifiedUser()
        `when`(userRepository.findByEmail("user@example.com")).thenReturn(user)

        service.forgotPassword("user@example.com")

        assertEquals(0, mailSender.sent.size)
        verify(passwordResetRepository, never()).save(anyKt())
    }

    @Test
    fun `forgotPassword does nothing within cooldown period`() {
        val user = verifiedUser()
        `when`(userRepository.findByEmail("user@example.com")).thenReturn(user)
        `when`(passwordResetRepository.findLatestByUserId(anyLong())).thenReturn(
            pendingRow(user.id!!, createdSecondsAgo = 30),
        )

        service.forgotPassword("user@example.com")

        assertEquals(0, mailSender.sent.size)
    }

    @Test
    fun `forgotPassword issues replacement reset after cooldown elapses`() {
        val user = verifiedUser()
        `when`(userRepository.findByEmail("user@example.com")).thenReturn(user)
        `when`(passwordResetRepository.findLatestByUserId(anyLong())).thenReturn(
            pendingRow(user.id!!, createdSecondsAgo = 90),
        )

        service.forgotPassword("user@example.com")

        assertEquals(1, mailSender.sent.size)
        verify(passwordResetRepository).consumeAllPendingByUserId(anyLong(), anyKt())
    }

    @Test
    fun `forgotPassword returns normally on delivery failure (same public response)`() {
        val failingMailSender = mock(MailSender::class.java)
        doThrow(RuntimeException("SMTP error")).`when`(failingMailSender).send(anyString(), anyString(), anyString(), anyKt())
        val user = verifiedUser()
        `when`(userRepository.findByEmail("user@example.com")).thenReturn(user)
        `when`(passwordResetRepository.findLatestByUserId(anyLong())).thenReturn(null)
        val failService = PasswordResetService(
            passwordResetRepository = passwordResetRepository,
            userRepository = userRepository,
            mailSender = failingMailSender,
            passwordEncoder = passwordEncoder,
            rememberMeTokenRepository = rememberMeTokenRepository,
            sessionRevocationService = sessionRevocationService,
            props = props,
            frontendBaseUrl = "http://localhost:5173",
        )

        failService.forgotPassword("user@example.com")

        // consumeAllPendingByUserId called twice: before save and on delivery failure
        verify(passwordResetRepository, times(2)).consumeAllPendingByUserId(anyLong(), anyKt())
    }

    // ── issueReset ────────────────────────────────────────────────────────────

    @Test
    fun `issueReset sends email with resetUrl containing resetId and token`() {
        service.issueReset(1L, "user@example.com")

        assertEquals(1, mailSender.sent.size)
        val msg = mailSender.sent[0]
        assertEquals("user@example.com", msg.to)
        val resetUrl = msg.variables["resetUrl"] as String
        assertTrue(resetUrl.startsWith("http://localhost:5173/reset-password?resetId="), "resetUrl must include resetId")
        assertTrue(resetUrl.contains("&token="), "resetUrl must include token")
    }

    @Test
    fun `issueReset stores SHA-256 hash of the generated token`() {
        service.issueReset(1L, "user@example.com")

        val resetUrl = mailSender.sent[0].variables["resetUrl"] as String
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
