package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.model.RegisterBody
import com.davismariotti.campalert.api.model.VerificationStatus
import com.davismariotti.campalert.exception.ConflictException
import com.davismariotti.campalert.model.Invite
import com.davismariotti.campalert.model.PlatformSettings
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.repository.PlatformSettingsRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.security.GroupMembershipService
import com.davismariotti.campalert.security.RememberMeServices
import com.davismariotti.campalert.security.UserDetailsServiceImpl
import com.davismariotti.campalert.service.SessionRevocationService
import com.davismariotti.campalert.service.email.EmailVerificationService
import com.davismariotti.campalert.service.email.PasswordResetService
import com.davismariotti.campalert.service.invite.InviteException
import com.davismariotti.campalert.service.invite.InviteService
import com.davismariotti.campalert.service.notification.NotificationService
import com.davismariotti.campalert.service.redis.ForgotPasswordRateLimiter
import com.davismariotti.campalert.service.turnstile.TurnstileService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository
import java.time.Instant
import java.util.Optional
import java.util.UUID

/** Covers invite-only-mode gating and invite redemption branching in AuthDelegateImpl.registerAfterTurnstile. */
class AuthRegisterInviteTest {
    private val userRepository = mock(UserRepository::class.java)
    private val emailVerificationService = mock(EmailVerificationService::class.java)
    private val userDetailsService = mock(UserDetailsServiceImpl::class.java)
    private val inviteService = mock(InviteService::class.java)
    private val platformSettingsRepository = mock(PlatformSettingsRepository::class.java)
    private val groupMembershipService = mock(GroupMembershipService::class.java)
    private val request = mock(HttpServletRequest::class.java)
    private val session = mock(HttpSession::class.java)

    private val delegate = AuthDelegateImpl(
        userRepository = userRepository,
        passwordEncoder = BCryptPasswordEncoder(),
        authenticationManager = mock(AuthenticationManager::class.java),
        request = request,
        response = mock(HttpServletResponse::class.java),
        rememberMeServices = mock(RememberMeServices::class.java),
        emailVerificationService = emailVerificationService,
        passwordResetService = mock(PasswordResetService::class.java),
        userDetailsService = userDetailsService,
        sessionRevocationService = mock(SessionRevocationService::class.java),
        rememberMeTokenRepository = mock(PersistentTokenRepository::class.java),
        notificationService = mock(NotificationService::class.java),
        forgotPasswordRateLimiter = mock(ForgotPasswordRateLimiter::class.java),
        turnstileService = mock(TurnstileService::class.java),
        groupMembershipService = groupMembershipService,
        inviteService = inviteService,
        platformSettingsRepository = platformSettingsRepository,
        frontendBaseUrl = "http://localhost:5173",
        self = mock(AuthDelegateImpl::class.java),
    )

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun body(email: String = "new@example.com", inviteId: UUID? = null, inviteToken: String? = null) =
        RegisterBody(
            email = email,
            password = "password1",
            timezone = "UTC",
            turnstileToken = "ok",
            inviteId = inviteId,
            inviteToken = inviteToken,
        )

    private fun savedUser(id: Long = 1L, email: String = "new@example.com") = User(id = id, email = email, passwordHash = "hash")

    @Test
    fun `open registration proceeds when invite-only mode is disabled`() {
        `when`(platformSettingsRepository.findById(PlatformSettings.SINGLETON_ID)).thenReturn(Optional.empty())
        `when`(userRepository.findByEmail("new@example.com")).thenReturn(null)
        `when`(userRepository.save(anyKt())).thenReturn(savedUser())
        `when`(emailVerificationService.issueVerification(anyLong(), anyString())).thenReturn(UUID.randomUUID())

        val response = delegate.registerAfterTurnstile(body())

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals(VerificationStatus.PENDING_VERIFICATION, response.body!!.verificationStatus)
        verify(inviteService, never()).validateAndLock(anyKt(), anyString(), anyString())
    }

    @Test
    fun `registration without an invite is rejected when invite-only mode is enabled`() {
        `when`(platformSettingsRepository.findById(PlatformSettings.SINGLETON_ID))
            .thenReturn(Optional.of(PlatformSettings(inviteOnlyEnabled = true)))

        assertThrows(InviteException.Required::class.java) {
            delegate.registerAfterTurnstile(body())
        }

        verify(userRepository, never()).save(anyKt())
    }

    @Test
    fun `registration with a valid invite succeeds even when invite-only mode is disabled`() {
        val invite = linkInvite()
        `when`(inviteService.validateAndLock(invite.id, "token", "new@example.com")).thenReturn(invite)
        `when`(userRepository.findByEmail("new@example.com")).thenReturn(null)
        `when`(userRepository.save(anyKt())).thenReturn(savedUser())
        `when`(emailVerificationService.issueVerification(anyLong(), anyString())).thenReturn(UUID.randomUUID())

        val response = delegate.registerAfterTurnstile(body(inviteId = invite.id, inviteToken = "token"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        verify(inviteService).recordRedemption(invite, 1L)
    }

    @Test
    fun `an already-registered email is rejected even with a valid invite`() {
        val invite = linkInvite()
        `when`(inviteService.validateAndLock(invite.id, "token", "new@example.com")).thenReturn(invite)
        `when`(userRepository.findByEmail("new@example.com")).thenReturn(savedUser())

        assertThrows(ConflictException::class.java) {
            delegate.registerAfterTurnstile(body(inviteId = invite.id, inviteToken = "token"))
        }

        verify(userRepository, never()).save(anyKt())
    }

    @Test
    fun `redeeming a public link still requires normal OTP verification`() {
        val invite = linkInvite()
        `when`(inviteService.validateAndLock(invite.id, "token", "new@example.com")).thenReturn(invite)
        `when`(userRepository.findByEmail("new@example.com")).thenReturn(null)
        `when`(userRepository.save(anyKt())).thenReturn(savedUser())
        `when`(emailVerificationService.issueVerification(anyLong(), anyString())).thenReturn(UUID.randomUUID())

        val response = delegate.registerAfterTurnstile(body(inviteId = invite.id, inviteToken = "token"))

        assertEquals(VerificationStatus.PENDING_VERIFICATION, response.body!!.verificationStatus)
        verify(emailVerificationService).issueVerification(1L, "new@example.com")
        verify(request, never()).getSession(true)
    }

    @Test
    fun `redeeming an email-tied invite auto-verifies and establishes a session without issuing an OTP`() {
        val invite = emailInvite("new@example.com")
        `when`(inviteService.validateAndLock(invite.id, "token", "new@example.com")).thenReturn(invite)
        `when`(userRepository.findByEmail("new@example.com")).thenReturn(null)
        `when`(userRepository.save(anyKt())).thenReturn(savedUser())
        `when`(request.getSession(true)).thenReturn(session)
        val userDetails = org.springframework.security.core.userdetails.User(
            "new@example.com",
            "hash",
            listOf(SimpleGrantedAuthority("VIEW_PROFILE")),
        )
        `when`(userDetailsService.loadUserByUsername("new@example.com")).thenReturn(userDetails)

        val response = delegate.registerAfterTurnstile(body(inviteId = invite.id, inviteToken = "token"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals(VerificationStatus.VERIFIED, response.body!!.verificationStatus)
        assertNull(response.body!!.verificationId)
        verify(emailVerificationService, never()).issueVerification(anyLong(), anyString())
        verify(request).getSession(true)
    }

    private fun linkInvite() =
        Invite(
            id = UUID.randomUUID(),
            email = null,
            tokenHash = "hash",
            maxUses = 5,
            usedCount = 0,
            createdByUserId = 9L,
            createdAt = Instant.now(),
            expiresAt = Instant.now().plusSeconds(3600),
        )

    private fun emailInvite(email: String) =
        Invite(
            id = UUID.randomUUID(),
            email = email,
            tokenHash = "hash",
            maxUses = 1,
            usedCount = 0,
            createdByUserId = 9L,
            createdAt = Instant.now(),
            expiresAt = Instant.now().plusSeconds(3600),
        )

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyKt(): T = org.mockito.ArgumentMatchers.any<T>() as T
}
