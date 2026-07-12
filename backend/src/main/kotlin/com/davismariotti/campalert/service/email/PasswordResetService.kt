package com.davismariotti.campalert.service.email

import com.davismariotti.campalert.config.PasswordResetProperties
import com.davismariotti.campalert.model.PasswordReset
import com.davismariotti.campalert.notification.PasswordChangedNotification
import com.davismariotti.campalert.notification.ResetPasswordNotification
import com.davismariotti.campalert.repository.PasswordResetRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.SessionRevocationService
import com.davismariotti.campalert.service.notification.NotificationService
import com.davismariotti.campalert.util.CryptoUtils
import com.davismariotti.notifications.SimpleRecipient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class PasswordResetService(
    private val passwordResetRepository: PasswordResetRepository,
    private val userRepository: UserRepository,
    private val notificationService: NotificationService,
    private val passwordEncoder: BCryptPasswordEncoder,
    private val rememberMeTokenRepository: PersistentTokenRepository,
    private val sessionRevocationService: SessionRevocationService,
    private val props: PasswordResetProperties,
    @Value("\${campfinder.email.frontend-base-url}") private val frontendBaseUrl: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val secureRandom = SecureRandom()

    enum class ResetResult { SUCCESS, INVALID_OR_EXPIRED, PASSWORD_TOO_WEAK, PASSWORD_SAME_AS_CURRENT }

    @Transactional
    fun issueReset(userId: Long, email: String): UUID {
        val now = Instant.now()
        passwordResetRepository.consumeAllPendingByUserId(userId, now)

        val tokenBytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        val token = tokenBytes.joinToString("") { "%02x".format(it) }
        val id = UUID.randomUUID()
        passwordResetRepository.save(
            PasswordReset(
                id = id,
                userId = userId,
                tokenHash = sha256(token),
                createdAt = now,
                expiresAt = now.plus(props.expiresIn),
            ),
        )

        notificationService.sendAsync(
            ResetPasswordNotification(
                resetUrl = "$frontendBaseUrl/reset-password?resetId=$id&token=$token",
                expiryMinutes = props.expiresIn.toMinutes().toString(),
                frontendBaseUrl = frontendBaseUrl,
            ),
            SimpleRecipient(email = email),
        )

        return id
    }

    fun forgotPassword(email: String) {
        val user = userRepository.findByEmail(email) ?: return
        if (user.emailVerifiedAt == null) return

        val userId = user.id!!
        val latest = passwordResetRepository.findLatestByUserId(userId)
        if (latest != null && Duration.between(latest.createdAt, Instant.now()) < props.resendCooldown) return

        issueReset(userId, user.email)
    }

    @Transactional
    fun consumeReset(resetId: UUID, token: String, newPassword: String): ResetResult {
        if (newPassword.length < 8 || newPassword.length > 72) return ResetResult.PASSWORD_TOO_WEAK

        val row = passwordResetRepository.findPendingByIdForUpdate(resetId)
            ?: return ResetResult.INVALID_OR_EXPIRED

        if (row.expiresAt.isBefore(Instant.now())) return ResetResult.INVALID_OR_EXPIRED

        if (!CryptoUtils.constantTimeEquals(sha256(token), row.tokenHash)) return ResetResult.INVALID_OR_EXPIRED

        val user = userRepository.findById(row.userId).orElseThrow()

        if (passwordEncoder.matches(newPassword, user.passwordHash)) return ResetResult.PASSWORD_SAME_AS_CURRENT

        val now = Instant.now()
        userRepository.save(user.copy(passwordHash = passwordEncoder.encode(newPassword)!!))
        passwordResetRepository.save(row.copy(consumedAt = now))
        passwordResetRepository.consumeAllPendingByUserId(row.userId, now)

        rememberMeTokenRepository.removeUserTokens(user.email)
        sessionRevocationService.revokeAllSessionsFor(user.email)

        notificationService.sendAsync(PasswordChangedNotification(frontendBaseUrl), SimpleRecipient(email = user.email))

        return ResetResult.SUCCESS
    }

    internal fun sha256(input: String): String = CryptoUtils.sha256(input)
}
