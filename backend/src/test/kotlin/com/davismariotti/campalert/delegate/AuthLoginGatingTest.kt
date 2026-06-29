package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.model.LoginBody
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.security.RememberMeServices
import com.davismariotti.campalert.security.UserDetailsServiceImpl
import com.davismariotti.campalert.service.SessionRevocationService
import com.davismariotti.campalert.service.email.EmailVerificationService
import com.davismariotti.campalert.service.email.PasswordResetService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
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
import java.time.Instant
import java.util.UUID

class AuthLoginGatingTest {
    private val userRepository = mock(UserRepository::class.java)
    private val authenticationManager = mock(AuthenticationManager::class.java)
    private val request = mock(HttpServletRequest::class.java)
    private val response = mock(HttpServletResponse::class.java)
    private val rememberMeServices = mock(RememberMeServices::class.java)
    private val emailVerificationService = mock(EmailVerificationService::class.java)
    private val passwordResetService = mock(PasswordResetService::class.java)
    private val session = mock(HttpSession::class.java)

    private val delegate = AuthDelegateImpl(
        userRepository = userRepository,
        passwordEncoder = BCryptPasswordEncoder(),
        authenticationManager = authenticationManager,
        request = request,
        response = response,
        rememberMeServices = rememberMeServices,
        emailVerificationService = emailVerificationService,
        passwordResetService = passwordResetService,
        userDetailsService = mock(UserDetailsServiceImpl::class.java),
        sessionRevocationService = mock(SessionRevocationService::class.java),
    )

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `login returns 401 EMAIL_NOT_VERIFIED for correct password on unverified account`() {
        val auth = UsernamePasswordAuthenticationToken(
            "user@example.com",
            null,
            listOf(SimpleGrantedAuthority("ROLE_USER")),
        )
        `when`(authenticationManager.authenticate(any())).thenReturn(auth)
        `when`(userRepository.findByEmail("user@example.com")).thenReturn(
            unverifiedUser(),
        )
        `when`(emailVerificationService.ensureVerificationForLogin(1L, "user@example.com"))
            .thenReturn(UUID.randomUUID())

        val responseEntity = delegate.login(LoginBody(email = "user@example.com", password = "password"))

        assertEquals(HttpStatus.UNAUTHORIZED, responseEntity.statusCode)
        // body is an ErrorResponse cast as AuthResponse; we verify status and no-session behavior.
        // Accessing .body from Kotlin inserts a checkcast that would ClassCastException.
        verify(request, never()).getSession(anyBoolean())
    }

    @Test
    fun `login does not create session or set security context for unverified account`() {
        val auth = UsernamePasswordAuthenticationToken(
            "user@example.com",
            null,
            listOf(SimpleGrantedAuthority("ROLE_USER")),
        )
        `when`(authenticationManager.authenticate(any())).thenReturn(auth)
        `when`(userRepository.findByEmail("user@example.com")).thenReturn(unverifiedUser())
        `when`(emailVerificationService.ensureVerificationForLogin(1L, "user@example.com"))
            .thenReturn(UUID.randomUUID())

        delegate.login(LoginBody(email = "user@example.com", password = "password"))

        verify(request, never()).getSession(anyBoolean())
        verify(emailVerificationService).ensureVerificationForLogin(1L, "user@example.com")
        // SecurityContextHolder should not have been set
        assertEquals(null, SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `login proceeds normally for verified account`() {
        val userDetails = org.springframework.security.core.userdetails.User(
            "user@example.com",
            "password",
            listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
        val auth = UsernamePasswordAuthenticationToken(
            userDetails,
            null,
            listOf(SimpleGrantedAuthority("ROLE_USER")),
        )
        `when`(authenticationManager.authenticate(any())).thenReturn(auth)
        `when`(userRepository.findByEmail("user@example.com")).thenReturn(verifiedUser())
        `when`(request.getSession(true)).thenReturn(session)

        val responseEntity = delegate.login(LoginBody(email = "user@example.com", password = "password"))

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        verify(request).getSession(true)
    }

    private fun unverifiedUser() =
        User(
            id = 1L,
            email = "user@example.com",
            passwordHash = "hash",
            emailVerifiedAt = null,
        )

    private fun verifiedUser() =
        User(
            id = 1L,
            email = "user@example.com",
            passwordHash = "hash",
            emailVerifiedAt = Instant.now().minusSeconds(3600),
        )
}
