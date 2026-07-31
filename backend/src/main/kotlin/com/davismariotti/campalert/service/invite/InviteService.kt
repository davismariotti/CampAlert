package com.davismariotti.campalert.service.invite

import com.davismariotti.campalert.exception.ConflictException
import com.davismariotti.campalert.exception.NotFoundException
import com.davismariotti.campalert.model.Invite
import com.davismariotti.campalert.model.InviteRedemption
import com.davismariotti.campalert.notification.InviteNotification
import com.davismariotti.campalert.repository.InviteRedemptionRepository
import com.davismariotti.campalert.repository.InviteRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.notification.NotificationService
import com.davismariotti.campalert.util.CryptoUtils
import com.davismariotti.notifications.SimpleRecipient
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class InviteService(
    private val inviteRepository: InviteRepository,
    private val inviteRedemptionRepository: InviteRedemptionRepository,
    private val userRepository: UserRepository,
    private val notificationService: NotificationService,
    @param:Value($$"${campfinder.email.frontend-base-url}") private val frontendBaseUrl: String,
) {
    private val secureRandom = SecureRandom()

    companion object {
        val DEFAULT_EXPIRY: Duration = Duration.ofDays(7)
    }

    data class EmailInviteResult(
        val email: String,
        val created: Boolean,
        val reason: String? = null
    )

    @Transactional
    fun createEmailInvites(emails: List<String>, expiresInDays: Long?, actingAdminId: Long): List<EmailInviteResult> {
        val expiry = expiresInDays?.let { Duration.ofDays(it) } ?: DEFAULT_EXPIRY
        return emails.map { email -> createSingleEmailInvite(email, expiry, actingAdminId) }
    }

    private fun createSingleEmailInvite(email: String, expiry: Duration, actingAdminId: Long): EmailInviteResult {
        if (userRepository.findByEmail(email) != null) {
            return EmailInviteResult(email, created = false, reason = "ALREADY_REGISTERED")
        }
        val (invite, token) = createInvite(email = email, maxUses = 1, expiry = expiry, actingAdminId = actingAdminId)
        val encodedEmail = java.net.URLEncoder.encode(email, Charsets.UTF_8)
        notificationService.sendAsync(
            InviteNotification(
                // email= is a UX convenience only (lets the frontend pre-fill/lock the field) — the
                // recipient already knows their own address since the link was mailed there; the
                // register endpoint still independently validates the submitted email against the
                // invite server-side, so this is not a trust boundary.
                inviteUrl = "$frontendBaseUrl/register?inviteId=${invite.id}&token=$token&email=$encodedEmail",
                expiryDays = expiry.toDays().coerceAtLeast(1).toString(),
                frontendBaseUrl = frontendBaseUrl,
            ),
            SimpleRecipient(email = email),
        )
        return EmailInviteResult(email, created = true)
    }

    /** Returns the created invite plus its plaintext token — the only time the token is ever available; only tokenHash is persisted. */
    @Transactional
    fun createPublicLink(maxUses: Int, expiresInDays: Long?, actingAdminId: Long): Pair<Invite, String> {
        val expiry = expiresInDays?.let { Duration.ofDays(it) } ?: DEFAULT_EXPIRY
        return createInvite(email = null, maxUses = maxUses, expiry = expiry, actingAdminId = actingAdminId)
    }

    private fun createInvite(
        email: String?,
        maxUses: Int,
        expiry: Duration,
        actingAdminId: Long
    ): Pair<Invite, String> {
        val tokenBytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        val token = tokenBytes.joinToString("") { "%02x".format(it) }
        val now = Instant.now()
        val invite = inviteRepository.save(
            Invite(
                id = UUID.randomUUID(),
                email = email,
                tokenHash = CryptoUtils.sha256(token),
                maxUses = maxUses,
                createdByUserId = actingAdminId,
                createdAt = now,
                expiresAt = now.plus(expiry),
            ),
        )
        return invite to token
    }

    fun listInvites(includeInactive: Boolean, pageable: Pageable): Page<Invite> = inviteRepository.findAllFiltered(includeInactive, Instant.now(), pageable)

    @Transactional
    fun deactivateInvite(inviteId: UUID): Invite {
        val invite = inviteRepository.findByIdForUpdate(inviteId) ?: throw NotFoundException("Invite not found")
        if (invite.deactivatedAt != null) throw ConflictException("Invite is already deactivated")
        return inviteRepository.save(invite.copy(deactivatedAt = Instant.now()))
    }

    /**
     * Locks and validates an invite for redemption during registration. Must run in the same
     * transaction as the eventual [recordRedemption] call (and the user-creation in between) so the
     * pessimistic write lock covers the whole capacity check — see design.md D1 concurrency note.
     */
    @Transactional
    fun validateAndLock(inviteId: UUID, token: String, submittedEmail: String): Invite {
        val invite = inviteRepository.findByIdForUpdate(inviteId)
            ?: throw InviteException.InvalidOrExpired(isEmailInvite = false)

        if (!CryptoUtils.constantTimeEquals(CryptoUtils.sha256(token), invite.tokenHash)) {
            throw InviteException.InvalidOrExpired(isEmailInvite = invite.email != null)
        }
        if (invite.deactivatedAt != null) throw InviteException.Deactivated(invite.email != null)
        if (invite.usedCount >= invite.maxUses) throw InviteException.Exhausted(invite.email != null)
        if (invite.expiresAt.isBefore(Instant.now())) throw InviteException.InvalidOrExpired(invite.email != null)
        if (invite.email != null && !invite.email.equals(submittedEmail, ignoreCase = true)) {
            throw InviteException.EmailMismatch()
        }
        return invite
    }

    @Transactional
    fun recordRedemption(invite: Invite, userId: Long) {
        inviteRedemptionRepository.save(
            InviteRedemption(id = UUID.randomUUID(), inviteId = invite.id, userId = userId, redeemedAt = Instant.now()),
        )
        inviteRepository.save(invite.copy(usedCount = invite.usedCount + 1))
    }
}
