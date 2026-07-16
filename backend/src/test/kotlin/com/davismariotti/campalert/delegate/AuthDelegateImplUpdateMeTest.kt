package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.model.UpdateMeBody
import com.davismariotti.campalert.exception.BadRequestException
import com.davismariotti.campalert.model.User
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository

class AuthDelegateImplUpdateMeTest {
    private val userRepository = mock(UserRepository::class.java)

    private val delegate = AuthDelegateImpl(
        userRepository = userRepository,
        passwordEncoder = BCryptPasswordEncoder(),
        authenticationManager = mock(AuthenticationManager::class.java),
        request = mock(HttpServletRequest::class.java),
        response = mock(HttpServletResponse::class.java),
        rememberMeServices = mock(RememberMeServices::class.java),
        emailVerificationService = mock(EmailVerificationService::class.java),
        passwordResetService = mock(PasswordResetService::class.java),
        userDetailsService = mock(UserDetailsServiceImpl::class.java),
        sessionRevocationService = mock(SessionRevocationService::class.java),
        rememberMeTokenRepository = mock(PersistentTokenRepository::class.java),
        notificationService = mock(NotificationService::class.java),
        forgotPasswordRateLimiter = mock(ForgotPasswordRateLimiter::class.java),
        turnstileService = mock(TurnstileService::class.java),
        frontendBaseUrl = "http://localhost:5173",
    )

    private val user = User(
        id = 1L,
        email = "user@example.com",
        passwordHash = "hash",
        timezone = "America/Los_Angeles",
    )

    @BeforeEach
    fun setUp() {
        val auth = UsernamePasswordAuthenticationToken(user.email, null, listOf(SimpleGrantedAuthority("ROLE_USER")))
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = auth
        SecurityContextHolder.setContext(context)
        `when`(userRepository.findByEmail(user.email)).thenReturn(user)
        `when`(userRepository.save(anyKt())).thenAnswer { it.arguments[0] }
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `setting pushover keys and enabling the override persists all three fields`() {
        val result = delegate.updateMe(
            UpdateMeBody(
                pushoverApiToken = "app-token",
                pushoverUserKey = "user-key",
                pushoverOverrideEnabled = true,
            ),
        )

        val body = result.body!!
        assertEquals("app-token", body.pushoverApiToken)
        assertEquals("user-key", body.pushoverUserKey)
        assertEquals(true, body.pushoverOverrideEnabled)
        assertEquals("America/Los_Angeles", body.timezone)
    }

    @Test
    fun `omitted fields are left unchanged`() {
        val existing = user.copy(
            pushoverApiToken = "existing-token",
            pushoverUserKey = "existing-key",
            pushoverOverrideEnabled = true,
        )
        `when`(userRepository.findByEmail(user.email)).thenReturn(existing)

        val result = delegate.updateMe(UpdateMeBody(timezone = "America/Denver"))

        val body = result.body!!
        assertEquals("America/Denver", body.timezone)
        assertEquals("existing-token", body.pushoverApiToken)
        assertEquals("existing-key", body.pushoverUserKey)
        assertEquals(true, body.pushoverOverrideEnabled)
    }

    @Test
    fun `explicit false disables the override even when keys remain set`() {
        val existing = user.copy(
            pushoverApiToken = "existing-token",
            pushoverUserKey = "existing-key",
            pushoverOverrideEnabled = true,
        )
        `when`(userRepository.findByEmail(user.email)).thenReturn(existing)

        val result = delegate.updateMe(UpdateMeBody(pushoverOverrideEnabled = false))

        val body = result.body!!
        assertEquals(false, body.pushoverOverrideEnabled)
        assertEquals("existing-token", body.pushoverApiToken)
        assertEquals("existing-key", body.pushoverUserKey)
    }

    @Test
    fun `enabling the override with no keys set returns 400 and does not persist`() {
        val ex = assertThrows(BadRequestException::class.java) {
            delegate.updateMe(UpdateMeBody(pushoverOverrideEnabled = true))
        }

        assertEquals("Pushover app token and user key are required to enable the Pushover override", ex.message)
        verify(userRepository, never()).save(anyKt())
    }

    @Test
    fun `enabling the override with only the api token set returns 400`() {
        assertThrows(BadRequestException::class.java) {
            delegate.updateMe(UpdateMeBody(pushoverApiToken = "app-token", pushoverOverrideEnabled = true))
        }
        verify(userRepository, never()).save(anyKt())
    }

    @Test
    fun `enabling the override with only the user key set returns 400`() {
        assertThrows(BadRequestException::class.java) {
            delegate.updateMe(UpdateMeBody(pushoverUserKey = "user-key", pushoverOverrideEnabled = true))
        }
        verify(userRepository, never()).save(anyKt())
    }

    @Test
    fun `enabling the override reusing an already-stored key from a previous save is allowed`() {
        val existing = user.copy(pushoverApiToken = "existing-token", pushoverUserKey = "existing-key")
        `when`(userRepository.findByEmail(user.email)).thenReturn(existing)

        val result = delegate.updateMe(UpdateMeBody(pushoverOverrideEnabled = true))

        val body = result.body!!
        assertEquals(true, body.pushoverOverrideEnabled)
        assertEquals("existing-token", body.pushoverApiToken)
        assertEquals("existing-key", body.pushoverUserKey)
    }

    @Test
    fun `getMe returns pushover fields`() {
        val existing = user.copy(pushoverApiToken = "app-token", pushoverUserKey = "user-key", pushoverOverrideEnabled = true)
        `when`(userRepository.findByEmail(user.email)).thenReturn(existing)

        val result = delegate.getMe()

        val body = result.body!!
        assertEquals("app-token", body.pushoverApiToken)
        assertEquals("user-key", body.pushoverUserKey)
        assertEquals(true, body.pushoverOverrideEnabled)
    }

    @Test
    fun `new user has no pushover keys and override disabled by default`() {
        val result = delegate.getMe()

        val body = result.body!!
        assertNull(body.pushoverApiToken)
        assertNull(body.pushoverUserKey)
        assertEquals(false, body.pushoverOverrideEnabled)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyKt(): T = org.mockito.ArgumentMatchers.any<T>() as T
}
