package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.model.RegisterBody
import com.davismariotti.campalert.api.model.ResendVerificationBody
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.security.RememberMeServices
import com.davismariotti.campalert.security.UserDetailsServiceImpl
import com.davismariotti.campalert.service.SessionRevocationService
import com.davismariotti.campalert.service.email.EmailVerificationService
import com.davismariotti.campalert.service.email.PasswordResetService
import com.davismariotti.campalert.service.notification.NotificationService
import com.davismariotti.campalert.service.redis.ForgotPasswordRateLimiter
import com.davismariotti.campalert.service.turnstile.TurnstileFailedException
import com.davismariotti.campalert.service.turnstile.TurnstileService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository

/** Covers the TURNSTILE_FAILED rejection path on register()/resendVerification() — the happy path is exercised elsewhere. */
class AuthTurnstileGateTest {
    private val userRepository = mock(UserRepository::class.java)
    private val emailVerificationService = mock(EmailVerificationService::class.java)
    private val turnstileService = mock(TurnstileService::class.java)

    private val delegate = AuthDelegateImpl(
        userRepository = userRepository,
        passwordEncoder = BCryptPasswordEncoder(),
        authenticationManager = mock(AuthenticationManager::class.java),
        request = mock(HttpServletRequest::class.java),
        response = mock(HttpServletResponse::class.java),
        rememberMeServices = mock(RememberMeServices::class.java),
        emailVerificationService = emailVerificationService,
        passwordResetService = mock(PasswordResetService::class.java),
        userDetailsService = mock(UserDetailsServiceImpl::class.java),
        sessionRevocationService = mock(SessionRevocationService::class.java),
        rememberMeTokenRepository = mock(PersistentTokenRepository::class.java),
        notificationService = mock(NotificationService::class.java),
        forgotPasswordRateLimiter = mock(ForgotPasswordRateLimiter::class.java),
        turnstileService = turnstileService,
        frontendBaseUrl = "http://localhost:5173",
    )

    @Test
    fun `register throws TurnstileFailedException and never creates a user when verification fails`() {
        `when`(turnstileService.verify(anyString())).thenThrow(TurnstileFailedException())

        assertThrows(TurnstileFailedException::class.java) {
            delegate.register(
                RegisterBody(email = "bot@example.com", password = "password1", timezone = "UTC", turnstileToken = "bad"),
            )
        }

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any())
        verify(emailVerificationService, never()).issueVerification(org.mockito.ArgumentMatchers.anyLong(), anyString())
    }

    @Test
    fun `resendVerification throws TurnstileFailedException and never triggers a resend when verification fails`() {
        `when`(turnstileService.verify(anyString())).thenThrow(TurnstileFailedException())

        assertThrows(TurnstileFailedException::class.java) {
            delegate.resendVerification(ResendVerificationBody(email = "bot@example.com", turnstileToken = "bad"))
        }

        verify(emailVerificationService, never()).resendVerification(anyString())
    }
}
