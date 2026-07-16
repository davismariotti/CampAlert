package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.model.ChangePasswordBody
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.notification.PasswordChangedNotification
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.security.RememberMeServices
import com.davismariotti.campalert.security.UserDetailsServiceImpl
import com.davismariotti.campalert.service.SessionRevocationService
import com.davismariotti.campalert.service.email.EmailVerificationService
import com.davismariotti.campalert.service.email.PasswordResetService
import com.davismariotti.campalert.service.notification.NotificationService
import com.davismariotti.campalert.service.redis.ForgotPasswordRateLimiter
import com.davismariotti.campalert.service.turnstile.TurnstileService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository
import java.time.Instant

class AuthDelegateImplChangePasswordTest {
    private val userRepository = mock(UserRepository::class.java)
    private val passwordEncoder = BCryptPasswordEncoder()
    private val request = mock(HttpServletRequest::class.java)
    private val session = mock(HttpSession::class.java)
    private val sessionRevocationService = mock(SessionRevocationService::class.java)
    private val rememberMeTokenRepository = mock(PersistentTokenRepository::class.java)
    private val notificationService = mock(NotificationService::class.java)

    private val delegate = AuthDelegateImpl(
        userRepository = userRepository,
        passwordEncoder = passwordEncoder,
        authenticationManager = mock(AuthenticationManager::class.java),
        request = request,
        response = mock(HttpServletResponse::class.java),
        rememberMeServices = mock(RememberMeServices::class.java),
        emailVerificationService = mock(EmailVerificationService::class.java),
        passwordResetService = mock(PasswordResetService::class.java),
        userDetailsService = mock(UserDetailsServiceImpl::class.java),
        sessionRevocationService = sessionRevocationService,
        rememberMeTokenRepository = rememberMeTokenRepository,
        notificationService = notificationService,
        forgotPasswordRateLimiter = mock(ForgotPasswordRateLimiter::class.java),
        turnstileService = mock(TurnstileService::class.java),
        frontendBaseUrl = "http://localhost:5173",
    )

    private val user = User(
        id = 1L,
        email = "user@example.com",
        passwordHash = passwordEncoder.encode("password1")!!,
        emailVerifiedAt = Instant.now().minusSeconds(3600),
    )

    @BeforeEach
    fun setUp() {
        val auth = UsernamePasswordAuthenticationToken(user.email, null, listOf(SimpleGrantedAuthority("ROLE_USER")))
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = auth
        SecurityContextHolder.setContext(context)
        `when`(userRepository.findByEmail(user.email)).thenReturn(user)
        `when`(request.getSession(false)).thenReturn(session)
        `when`(session.id).thenReturn("session-id")
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `successful change revokes remember-me tokens for the user`() {
        val result = delegate.changePassword(ChangePasswordBody(currentPassword = "password1", newPassword = "newpassword1"))

        assertEquals(HttpStatus.NO_CONTENT, result.statusCode)
        verify(rememberMeTokenRepository).removeUserTokens(user.email)
    }

    @Test
    fun `successful change sends a password-changed notification`() {
        delegate.changePassword(ChangePasswordBody(currentPassword = "password1", newPassword = "newpassword1"))

        verify(notificationService).sendAsync(anyKt<PasswordChangedNotification>(), anyKt())
    }

    @Test
    fun `wrong current password does not revoke tokens or send a notification`() {
        val result = delegate.changePassword(ChangePasswordBody(currentPassword = "wrongpassword", newPassword = "newpassword1"))

        assertEquals(HttpStatus.BAD_REQUEST, result.statusCode)
        verify(rememberMeTokenRepository, never()).removeUserTokens(anyKt())
        verify(notificationService, never()).sendAsync(anyKt(), anyKt())
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyKt(): T = org.mockito.ArgumentMatchers.any<T>() as T
}
