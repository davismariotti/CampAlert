package com.davismariotti.campalert.service.email

import com.davismariotti.campalert.config.EmailVerificationProperties
import com.davismariotti.campalert.model.EmailVerification
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.notification.Notification
import com.davismariotti.campalert.notification.VerifyEmailNotification
import com.davismariotti.campalert.notification.WelcomeNotification
import com.davismariotti.campalert.repository.EmailVerificationRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.email.EmailVerificationService.VerifyResult
import com.davismariotti.campalert.service.notification.NotificationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.UUID

class EmailVerificationServiceTest {
    private val emailVerificationRepository = mock(EmailVerificationRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val notificationService = mock(NotificationService::class.java)
    private val props = EmailVerificationProperties(
        expiresIn = Duration.ofMinutes(10),
        resendCooldown = Duration.ofSeconds(60),
        maxAttempts = 5,
    )
    private val service = EmailVerificationService(
        emailVerificationRepository = emailVerificationRepository,
        userRepository = userRepository,
        props = props,
        notificationService = notificationService,
        frontendBaseUrl = "http://localhost:5173",
    )

    private val savedEmailVerifications = mutableListOf<EmailVerification>()
    private val savedUsers = mutableListOf<User>()

    @BeforeEach
    fun setUp() {
        savedEmailVerifications.clear()
        savedUsers.clear()

        `when`(emailVerificationRepository.consumeAllPendingByUserId(anyLong(), anyKt())).thenReturn(0)
        `when`(emailVerificationRepository.save(anyKt())).thenAnswer {
            (it.arguments[0] as? EmailVerification)?.also { row -> savedEmailVerifications.add(row) }
        }
        `when`(userRepository.save(anyKt())).thenAnswer {
            (it.arguments[0] as? User)?.also { user -> savedUsers.add(user) }
        }
    }

    // ── issueVerification ─────────────────────────────────────────────────────

    @Test
    fun `issueVerification sends VerifyEmailNotification with 6-digit code and verifyUrl`() {
        val sent = mutableListOf<Notification>()
        doAnswer {
            sent.add(it.getArgument(0))
            null
        }.`when`(notificationService).send(anyKt())

        service.issueVerification(1L, "user@example.com")

        assertEquals(1, sent.size)
        val params = (sent[0] as VerifyEmailNotification).getEmailParameters()
        val code = params["code"] as String
        assertTrue(code.matches(Regex("\\d{6}")), "code must be 6 digits, was: $code")
        val verifyUrl = params["verifyUrl"] as String
        assertTrue(
            verifyUrl.startsWith("http://localhost:5173/verify-email?verificationId="),
            "verifyUrl must include verificationId",
        )
    }

    @Test
    fun `issueVerification stores SHA-256 hash of the generated code`() {
        val sent = mutableListOf<Notification>()
        doAnswer {
            sent.add(it.getArgument(0))
            null
        }.`when`(notificationService).send(anyKt())

        service.issueVerification(1L, "user@example.com")

        val sentCode = (sent[0] as VerifyEmailNotification).getEmailParameters()["code"] as String
        assertEquals(1, savedEmailVerifications.size)
        assertEquals(service.sha256(sentCode), savedEmailVerifications[0].codeHash)
    }

    @Test
    fun `issueVerification consumes prior pending rows before saving new row`() {
        service.issueVerification(1L, "user@example.com")

        val ordered = inOrder(emailVerificationRepository)
        ordered.verify(emailVerificationRepository).consumeAllPendingByUserId(anyLong(), anyKt())
        ordered.verify(emailVerificationRepository).save(anyKt())
    }

    @Test
    fun `issueVerification on delivery failure consumes the new row and still returns a verificationId`() {
        doThrow(RuntimeException("SMTP error")).`when`(notificationService).send(anyKt<VerifyEmailNotification>())

        val id = service.issueVerification(1L, "user@example.com")

        assertNotNull(id)
        // consumeAllPendingByUserId called twice: before save and on delivery failure
        verify(emailVerificationRepository, times(2)).consumeAllPendingByUserId(anyLong(), anyKt())
    }

    // ── resendVerification ────────────────────────────────────────────────────

    @Test
    fun `resendVerification issues code to unverified account outside cooldown`() {
        val user = unverifiedUser()
        `when`(userRepository.findByEmail("user@example.com")).thenReturn(user)
        `when`(emailVerificationRepository.findLatestByUserId(anyLong())).thenReturn(null)

        service.resendVerification("user@example.com")

        verify(notificationService).send(anyKt<VerifyEmailNotification>())
    }

    @Test
    fun `resendVerification does nothing for unknown email (enumeration resistance)`() {
        `when`(userRepository.findByEmail("ghost@example.com")).thenReturn(null)

        service.resendVerification("ghost@example.com")

        verify(emailVerificationRepository, never()).save(anyKt())
        verify(notificationService, never()).send(anyKt())
    }

    @Test
    fun `resendVerification does nothing for already-verified account (enumeration resistance)`() {
        val user = verifiedUser()
        `when`(userRepository.findByEmail("user@example.com")).thenReturn(user)

        service.resendVerification("user@example.com")

        verify(emailVerificationRepository, never()).save(anyKt())
        verify(notificationService, never()).send(anyKt())
    }

    @Test
    fun `resendVerification does nothing within cooldown period`() {
        val user = unverifiedUser()
        `when`(userRepository.findByEmail("user@example.com")).thenReturn(user)
        val recentRow = pendingRow(user.id!!, createdSecondsAgo = 30)
        `when`(emailVerificationRepository.findLatestByUserId(anyLong())).thenReturn(recentRow)

        service.resendVerification("user@example.com")

        verify(notificationService, never()).send(anyKt())
    }

    @Test
    fun `resendVerification issues replacement code after cooldown elapses`() {
        val user = unverifiedUser()
        `when`(userRepository.findByEmail("user@example.com")).thenReturn(user)
        val oldRow = pendingRow(user.id!!, createdSecondsAgo = 90)
        `when`(emailVerificationRepository.findLatestByUserId(anyLong())).thenReturn(oldRow)

        service.resendVerification("user@example.com")

        verify(notificationService).send(anyKt<VerifyEmailNotification>())
        verify(emailVerificationRepository).consumeAllPendingByUserId(anyLong(), anyKt())
    }

    // ── ensureVerificationForLogin ─────────────────────────────────────────────

    @Test
    fun `ensureVerificationForLogin reuses a valid pending row`() {
        val row = pendingRow(userId = 1L)
        `when`(emailVerificationRepository.findLatestByUserId(1L)).thenReturn(row)

        val result = service.ensureVerificationForLogin(1L, "user@example.com")

        assertEquals(row.id, result)
        verify(emailVerificationRepository, never()).consumeAllPendingByUserId(anyLong(), anyKt())
        verify(notificationService, never()).send(anyKt())
    }

    @Test
    fun `ensureVerificationForLogin issues a code when no usable row exists`() {
        `when`(emailVerificationRepository.findLatestByUserId(1L)).thenReturn(null)

        val result = service.ensureVerificationForLogin(1L, "user@example.com")

        assertNotNull(result)
        verify(notificationService).send(anyKt<VerifyEmailNotification>())
        verify(emailVerificationRepository).consumeAllPendingByUserId(anyLong(), anyKt())
    }

    // ── consumeVerification ───────────────────────────────────────────────────

    @Test
    fun `consumeVerification returns SUCCESS and marks emailVerifiedAt for correct code`() {
        val code = "123456"
        val user = unverifiedUser()
        val row = pendingRow(user.id!!, codeHash = service.sha256(code))
        setupConsumeFlow(row, user)

        val result = service.consumeVerification(row.id, code)

        assertEquals(VerifyResult.SUCCESS, result)
        assertEquals(1, savedUsers.size)
        assertNotNull(savedUsers[0].emailVerifiedAt)
    }

    @Test
    fun `consumeVerification sends welcome email on first verification success`() {
        val code = "123456"
        val user = unverifiedUser()
        val row = pendingRow(user.id!!, codeHash = service.sha256(code))
        setupConsumeFlow(row, user)

        service.consumeVerification(row.id, code)

        verify(notificationService).sendAsync(anyKt<WelcomeNotification>())
    }

    @Test
    fun `consumeVerification does not send welcome email for already-verified account`() {
        val user = verifiedUser()
        val row = pendingRow(user.id!!, codeHash = service.sha256("123456"))
        setupConsumeFlow(row, user)

        service.consumeVerification(row.id, "123456")

        verify(notificationService, never()).sendAsync(anyKt())
    }

    @Test
    fun `consumeVerification returns WRONG_CODE and increments attempt counter`() {
        val user = unverifiedUser()
        val row = pendingRow(user.id!!, codeHash = service.sha256("999999"))
        setupConsumeFlow(row, user)

        val result = service.consumeVerification(row.id, "123456")

        assertEquals(VerifyResult.WRONG_CODE, result)
        assertEquals(1, savedEmailVerifications.size)
        assertEquals(1.toShort(), savedEmailVerifications[0].attempts)
        assertTrue(savedUsers.isEmpty())
    }

    @Test
    fun `consumeVerification returns ATTEMPTS_EXCEEDED when limit reached`() {
        val user = unverifiedUser()
        val row = pendingRow(user.id!!, codeHash = service.sha256("999999"), attempts = 5)
        setupConsumeFlow(row, user)

        val result = service.consumeVerification(row.id, "123456")

        assertEquals(VerifyResult.ATTEMPTS_EXCEEDED, result)
        assertTrue(savedEmailVerifications.isEmpty())
        assertTrue(savedUsers.isEmpty())
    }

    @Test
    fun `consumeVerification returns INVALID_OR_EXPIRED for unknown verificationId`() {
        `when`(emailVerificationRepository.findPendingByIdForUpdate(anyKt())).thenReturn(null)

        val result = service.consumeVerification(UUID.randomUUID(), "123456")

        assertEquals(VerifyResult.INVALID_OR_EXPIRED, result)
    }

    @Test
    fun `consumeVerification returns INVALID_OR_EXPIRED for expired row`() {
        val user = unverifiedUser()
        val row = pendingRow(user.id!!, expiresAt = Instant.now().minusSeconds(1))
        setupConsumeFlow(row, user)

        val result = service.consumeVerification(row.id, "123456")

        assertEquals(VerifyResult.INVALID_OR_EXPIRED, result)
        assertTrue(savedEmailVerifications.isEmpty())
    }

    @Test
    fun `consumeVerification returns SUCCESS for already-verified account and consumes the row`() {
        val user = verifiedUser()
        val row = pendingRow(user.id!!, codeHash = service.sha256("123456"))
        setupConsumeFlow(row, user)

        val result = service.consumeVerification(row.id, "123456")

        assertEquals(VerifyResult.SUCCESS, result)
        assertEquals(1, savedEmailVerifications.size)
        assertNotNull(savedEmailVerifications[0].consumedAt)
        assertTrue(savedUsers.isEmpty())
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyKt(): T = org.mockito.ArgumentMatchers.any<T>() as T

    private fun unverifiedUser(id: Long = 1L) =
        User(
            id = id,
            email = "user@example.com",
            passwordHash = "hash",
            emailVerifiedAt = null,
        )

    private fun verifiedUser(id: Long = 1L) =
        User(
            id = id,
            email = "user@example.com",
            passwordHash = "hash",
            emailVerifiedAt = Instant.now().minusSeconds(3600),
        )

    private fun pendingRow(
        userId: Long,
        codeHash: String = service.sha256("000000"),
        attempts: Int = 0,
        createdSecondsAgo: Long = 300,
        expiresAt: Instant = Instant.now().plusSeconds(300),
    ) = EmailVerification(
        id = UUID.randomUUID(),
        userId = userId,
        codeHash = codeHash,
        attempts = attempts.toShort(),
        createdAt = Instant.now().minusSeconds(createdSecondsAgo),
        expiresAt = expiresAt,
        consumedAt = null,
    )

    private fun setupConsumeFlow(row: EmailVerification, user: User) {
        `when`(emailVerificationRepository.findPendingByIdForUpdate(row.id)).thenReturn(row)
        `when`(userRepository.findById(row.userId)).thenReturn(Optional.of(user))
    }
}
