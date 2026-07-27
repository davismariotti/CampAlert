package com.davismariotti.campalert.security

import com.davismariotti.campalert.api.model.LoginBody
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class LoginActivityIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `interactive login updates last_login_at`() {
        val verificationId = registerOnly()
        verifyLatestEmail(verificationId)
        assertThat(userRepository.findByEmail("user@test.com")!!.lastLoginAt).isNull()

        doPost("/api/auth/login", body = LoginBody(email = "user@test.com", password = "password1"))

        assertThat(userRepository.findByEmail("user@test.com")!!.lastLoginAt).isNotNull()
    }

    @Test
    fun `remember-me re-authentication updates last_login_at`() {
        val verificationId = registerOnly()
        verifyLatestEmail(verificationId)
        val loginResult = doPost(
            "/api/auth/login",
            body = LoginBody(email = "user@test.com", password = "password1", rememberMe = true),
        )
        val rememberMeCookie = loginResult.response.getCookie("remember-me")!!
        val afterInteractiveLogin = userRepository.findByEmail("user@test.com")!!.lastLoginAt!!

        Thread.sleep(50) // ensure the timestamp visibly advances between the two authentications
        // No SESSION cookie: forces re-authentication via RememberMeAuthenticationFilter, which
        // publishes its own AuthenticationSuccessEvent distinct from the interactive login above.
        mockMvc.perform(get("/api/auth/me").cookie(rememberMeCookie)).andReturn()

        val afterRememberMe = userRepository.findByEmail("user@test.com")!!.lastLoginAt!!
        assertThat(afterRememberMe).isAfter(afterInteractiveLogin)
    }
}
