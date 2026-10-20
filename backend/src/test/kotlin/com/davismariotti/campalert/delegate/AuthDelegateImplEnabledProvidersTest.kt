package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.model.ProviderType
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.security.RememberMeServices
import com.davismariotti.campalert.security.UserDetailsServiceImpl
import com.davismariotti.campalert.service.SessionRevocationService
import com.davismariotti.campalert.service.UserResourceLimitsService
import com.davismariotti.campalert.service.email.EmailVerificationService
import com.davismariotti.campalert.service.email.PasswordResetService
import com.davismariotti.campalert.service.notification.NotificationService
import com.davismariotti.campalert.service.redis.ForgotPasswordRateLimiter
import com.davismariotti.campalert.service.turnstile.TurnstileService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository

class AuthDelegateImplEnabledProvidersTest {
    private val userRepository = mock(UserRepository::class.java)
    private val limits = mock(UserResourceLimitsService::class.java)

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
        groupMembershipService = mock(com.davismariotti.campalert.security.GroupMembershipService::class.java),
        inviteService = mock(com.davismariotti.campalert.service.invite.InviteService::class.java),
        platformSettingsRepository = mock(com.davismariotti.campalert.repository.PlatformSettingsRepository::class.java),
        userResourceLimitsService = limits,
        frontendBaseUrl = "http://localhost:5173",
        self = mock(AuthDelegateImpl::class.java),
    )

    private val user = User(id = 7L, email = "user@example.com", passwordHash = "hash", timezone = "America/Los_Angeles")

    @BeforeEach
    fun setUp() {
        val auth = UsernamePasswordAuthenticationToken(user.email, null, listOf(SimpleGrantedAuthority("VIEW_PROFILE")))
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = auth
        SecurityContextHolder.setContext(context)
        `when`(userRepository.findByEmail(user.email)).thenReturn(user)
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun allow(vararg allowed: Provider) {
        Provider.entries.forEach { provider ->
            `when`(limits.isProviderAllowed(anyLong(), org.mockito.kotlin.eq(provider)))
                .thenReturn(UserResourceLimitsService.EffectiveValue(provider in allowed, UserResourceLimitsService.Source.GLOBAL_DEFAULT))
        }
    }

    @Test
    fun `getMe lists exactly the providers the user's effective access allows`() {
        allow(Provider.RECREATION_GOV, Provider.CAMPLIFE)

        val enabled = delegate.getMe().body!!.enabledProviders

        assertEquals(listOf(ProviderType.RECREATION_GOV, ProviderType.CAMPLIFE), enabled)
    }

    @Test
    fun `a user granted Global Entry sees it listed`() {
        allow(Provider.RECREATION_GOV, Provider.GLOBAL_ENTRY)

        assertEquals(listOf(ProviderType.RECREATION_GOV, ProviderType.GLOBAL_ENTRY), delegate.getMe().body!!.enabledProviders)
    }

    @Test
    fun `a user with no provider access gets an empty list`() {
        allow()

        assertEquals(emptyList<ProviderType>(), delegate.getMe().body!!.enabledProviders)
    }

    @Test
    fun `access is evaluated for the signed-in user`() {
        allow(Provider.RECREATION_GOV)

        delegate.getMe()

        Provider.entries.forEach { provider ->
            org.mockito.Mockito
                .verify(limits)
                .isProviderAllowed(user.id!!, provider)
        }
    }
}
