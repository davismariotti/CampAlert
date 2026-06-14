package com.davismariotti.campalert.security

import com.davismariotti.campalert.support.IntegrationTestBase
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class CsrfIntegrationTest : IntegrationTestBase() {
    @Test
    fun `GET request returns XSRF-TOKEN cookie that is not HttpOnly`() {
        val result = mockMvc.perform(get("/api/auth/me")).andReturn()
        val cookie = result.response.getCookie("XSRF-TOKEN")
        assertThat(cookie).isNotNull()
        assertThat(cookie!!.isHttpOnly).isFalse()
    }

    @Test
    fun `POST login without CSRF token returns 403`() {
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"test@test.com","password":"password1"}""")
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `POST login with valid CSRF token is not rejected with 403`() {
        val csrfToken = getCsrfToken()
        val result = mockMvc
            .perform(
                post("/api/auth/login")
                    .cookie(Cookie("XSRF-TOKEN", csrfToken))
                    .header("X-XSRF-TOKEN", csrfToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"test@test.com","password":"password1"}""")
            ).andReturn()
        assertThat(result.response.status).isNotEqualTo(HttpStatus.FORBIDDEN.value())
    }

    @Test
    fun `POST to SMS webhook without CSRF token reaches the handler`() {
        // CSRF is bypassed for the webhook — the XSRF-TOKEN cookie appearing in the response
        // proves the request passed through the CSRF filter rather than being blocked by it.
        // The controller itself returns 403 for an invalid Twilio signature, which is expected.
        val result = mockMvc
            .perform(
                post("/api/sms/webhook")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("Body", "STOP")
                    .param("From", "+15550000001")
            ).andReturn()
        assertThat(result.response.getCookie("XSRF-TOKEN")).isNotNull()
    }
}
