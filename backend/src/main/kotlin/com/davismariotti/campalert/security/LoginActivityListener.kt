package com.davismariotti.campalert.security

import com.davismariotti.campalert.repository.UserRepository
import org.springframework.context.event.EventListener
import org.springframework.security.authentication.event.AuthenticationSuccessEvent
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Updates `users.last_login_at` on every successful authentication. Spring Security publishes
 * [AuthenticationSuccessEvent] for both interactive form login and remember-me cookie-based
 * re-authentication (both go through the same [org.springframework.security.authentication.ProviderManager]),
 * so this one listener covers both without special-casing either auth path.
 */
@Component
class LoginActivityListener(
    private val userRepository: UserRepository,
) {
    @EventListener
    fun onAuthenticationSuccess(event: AuthenticationSuccessEvent) {
        val email = event.authentication.name
        val user = userRepository.findByEmail(email) ?: return
        userRepository.save(user.copy(lastLoginAt = Instant.now()))
    }
}
