package com.davismariotti.campalert.service.email

import com.davismariotti.campalert.config.EmailVerificationProperties
import com.davismariotti.campalert.model.EmailVerification
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.notification.VerifyEmailNotification
import com.davismariotti.campalert.notification.WelcomeNotification
import com.davismariotti.campalert.repository.EmailVerificationRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.notification.NotificationService
import com.davismariotti.campalert.util.CryptoUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class EmailVerificationService(
    private val emailVerificationRepository: EmailVerificationRepository,
    private val userRepository: UserRepository,
    private val props: EmailVerificationProperties,
    private val notificationService: NotificationService,
    @param:Value($$"${campfinder.email.frontend-base-url}") private val frontendBaseUrl: String,
    @Lazy private val self: EmailVerificationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val secureRandom = SecureRandom()

    enum class VerifyResult { SUCCESS, INVALID_OR_EXPIRED, ATTEMPTS_EXCEEDED, WRONG_CODE }

    /**
     * Generates a 6-digit code, writes a new email_verifications row (invalidating any prior pending rows),
     * and sends the verify template. On mail delivery failure, the new row is consumed so the code cannot
     * be used; the caller still receives the verificationId so the pending-registration response is uniform.
     */
    @Transactional
    fun issueVerification(userId: Long, email: String): UUID {
        val now = Instant.now()
        emailVerificationRepository.consumeAllPendingByUserId(userId, now)

        val code = "%06d".format(secureRandom.nextInt(1_000_000))
        val id = UUID.randomUUID()
        emailVerificationRepository.save(
            EmailVerification(
                id = id,
                userId = userId,
                codeHash = sha256(code),
                createdAt = now,
                expiresAt = now.plus(props.expiresIn),
            ),
        )

        val notificationUser = User(id = userId, email = email, passwordHash = "")
        notificationService.sendAsync(
            VerifyEmailNotification(
                user = notificationUser,
                code = code,
                verifyUrl = "$frontendBaseUrl/verify-email?verificationId=$id&code=$code",
                expiryMinutes = props.expiresIn.toMinutes().toString(),
                frontendBaseUrl = frontendBaseUrl,
            ),
        )

        return id
    }

    fun resendVerification(email: String) {
        val user = userRepository.findByEmail(email) ?: return
        if (user.emailVerifiedAt != null) return

        val userId = user.id!!
        val latest = emailVerificationRepository.findLatestByUserId(userId)
        if (latest != null && Duration.between(latest.createdAt, Instant.now()) < props.resendCooldown) return

        self.issueVerification(userId, user.email)
    }

    fun ensureVerificationForLogin(userId: Long, email: String): UUID {
        val now = Instant.now()
        val latest = emailVerificationRepository.findLatestByUserId(userId)
        if (
            latest != null &&
            latest.consumedAt == null &&
            latest.expiresAt.isAfter(now) &&
            latest.attempts.toInt() < props.maxAttempts
        ) {
            return latest.id
        }

        return issueVerification(userId, email)
    }

    data class VerifyOutcome(
        val result: VerifyResult,
        val user: User? = null
    )

    /**
     * Validates the submitted code against the pending email_verifications row.
     * Increments attempt count on wrong code; consumes and marks user verified on success.
     * Returns the verified User on SUCCESS so the caller can establish a session.
     */
    @Transactional
    fun consumeVerification(verificationId: UUID, code: String): VerifyOutcome {
        val row = emailVerificationRepository.findPendingByIdForUpdate(verificationId)
            ?: return VerifyOutcome(VerifyResult.INVALID_OR_EXPIRED)

        if (row.expiresAt.isBefore(Instant.now())) {
            return VerifyOutcome(VerifyResult.INVALID_OR_EXPIRED)
        }

        val user = userRepository.findById(row.userId).orElseThrow()

        if (user.emailVerifiedAt != null) {
            emailVerificationRepository.save(row.copy(consumedAt = Instant.now()))
            return VerifyOutcome(VerifyResult.SUCCESS, user)
        }

        if (row.attempts.toInt() >= props.maxAttempts) {
            return VerifyOutcome(VerifyResult.ATTEMPTS_EXCEEDED)
        }

        if (sha256(code) != row.codeHash) {
            emailVerificationRepository.save(row.copy(attempts = (row.attempts + 1).toShort()))
            return VerifyOutcome(VerifyResult.WRONG_CODE)
        }

        val now = Instant.now()
        emailVerificationRepository.save(row.copy(consumedAt = now))
        val verifiedUser = userRepository.save(user.copy(emailVerifiedAt = now))
        notificationService.sendAsync(WelcomeNotification(verifiedUser, frontendBaseUrl))
        return VerifyOutcome(VerifyResult.SUCCESS, verifiedUser)
    }

    internal fun sha256(input: String): String = CryptoUtils.sha256(input)
}
