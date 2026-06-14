package com.davismariotti.campalert.service.email

import com.davismariotti.campalert.config.EmailVerificationProperties
import com.davismariotti.campalert.model.EmailVerification
import com.davismariotti.campalert.repository.EmailVerificationRepository
import com.davismariotti.campalert.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class EmailVerificationService(
    private val emailVerificationRepository: EmailVerificationRepository,
    private val userRepository: UserRepository,
    private val mailSender: MailSender,
    private val props: EmailVerificationProperties,
    @Value("\${campfinder.email.frontend-base-url}") private val frontendBaseUrl: String,
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

        try {
            mailSender.send(
                to = email,
                subject = "Verify your CampAlert email",
                template = "email/verify",
                variables = mapOf(
                    "code" to code,
                    "verifyUrl" to "$frontendBaseUrl/verify-email?verificationId=$id",
                    "expiryMinutes" to props.expiresIn.toMinutes().toString(),
                ),
            )
        } catch (e: Exception) {
            log.warn("Verification email delivery failed for userId={}", userId)
            emailVerificationRepository.consumeAllPendingByUserId(userId, Instant.now())
        }

        return id
    }

    /**
     * Issues a replacement code for an unverified account. Enforces the resend cooldown.
     * Always returns normally — never surfaces whether an account exists.
     *
     * Note: calls issueVerification on the same bean instance (self-call bypasses the @Transactional
     * proxy). The individual repository operations inside issueVerification each participate in their
     * own transaction via @Modifying/@Transactional on the repository methods, which is sufficient
     * for this use case.
     */
    fun resendVerification(email: String) {
        val user = userRepository.findByEmail(email) ?: return
        if (user.emailVerifiedAt != null) return

        val userId = user.id!!
        val latest = emailVerificationRepository.findLatestByUserId(userId)
        if (latest != null && Duration.between(latest.createdAt, Instant.now()) < props.resendCooldown) return

        issueVerification(userId, user.email)
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

    /**
     * Validates the submitted code against the pending email_verifications row.
     * Increments attempt count on wrong code; consumes and marks user verified on success.
     */
    @Transactional
    fun consumeVerification(verificationId: UUID, code: String): VerifyResult {
        val row = emailVerificationRepository.findPendingByIdForUpdate(verificationId)
            ?: return VerifyResult.INVALID_OR_EXPIRED

        if (row.expiresAt.isBefore(Instant.now())) {
            return VerifyResult.INVALID_OR_EXPIRED
        }

        val user = userRepository.findById(row.userId).orElseThrow()

        if (user.emailVerifiedAt != null) {
            emailVerificationRepository.save(row.copy(consumedAt = Instant.now()))
            return VerifyResult.SUCCESS
        }

        if (row.attempts.toInt() >= props.maxAttempts) {
            return VerifyResult.ATTEMPTS_EXCEEDED
        }

        if (sha256(code) != row.codeHash) {
            emailVerificationRepository.save(row.copy(attempts = (row.attempts + 1).toShort()))
            return VerifyResult.WRONG_CODE
        }

        val now = Instant.now()
        emailVerificationRepository.save(row.copy(consumedAt = now))
        userRepository.save(user.copy(emailVerifiedAt = now))
        return VerifyResult.SUCCESS
    }

    internal fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
