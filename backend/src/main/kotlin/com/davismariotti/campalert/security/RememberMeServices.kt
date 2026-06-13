package com.davismariotti.campalert.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository

class RememberMeServices(
    key: String,
    userDetailsService: UserDetailsService,
    tokenRepository: PersistentTokenRepository,
) : PersistentTokenBasedRememberMeServices(key, userDetailsService, tokenRepository) {
    // loginSuccess() gates on a request parameter we don't have in a JSON API.
    // Call this directly when the caller has already decided remember-me should fire.
    fun loginSuccessForced(request: HttpServletRequest, response: HttpServletResponse, auth: Authentication) {
        onLoginSuccess(request, response, auth)
    }
}
