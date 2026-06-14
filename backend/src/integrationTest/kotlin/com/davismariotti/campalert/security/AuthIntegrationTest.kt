package com.davismariotti.campalert.security

import com.davismariotti.campalert.api.model.LoginBody
import com.davismariotti.campalert.api.model.RegisterBody
import com.davismariotti.campalert.api.model.UpdateMeBody
import com.davismariotti.campalert.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class AuthIntegrationTest : IntegrationTestBase() {
    // --- register ---

    @Test
    fun `successful registration returns 201 with verificationId in body`() {
        val result = doPost(
            "/api/auth/register",
            body = RegisterBody(email = "user@test.com", password = "password1", timezone = "America/Los_Angeles"),
        )
        assertThat(result.response.status).isEqualTo(201)
        assertThat(result.response.contentAsString).contains("verificationId")
        assertThat(result.response.contentAsString).contains("\"verificationStatus\":\"PENDING_VERIFICATION\"")
    }

    @Test
    fun `registration does not create a session`() {
        val result = doPost(
            "/api/auth/register",
            body = RegisterBody(email = "user@test.com", password = "password1", timezone = "America/Los_Angeles"),
        )
        assertThat(result.response.getCookie("SESSION")).isNull()
    }

    @Test
    fun `duplicate email registration returns 409`() {
        val body = RegisterBody(email = "user@test.com", password = "password1", timezone = "America/Los_Angeles")
        doPost("/api/auth/register", body = body)
        assertThat(doPost("/api/auth/register", body = body).response.status).isEqualTo(409)
    }

    @Test
    fun `password shorter than 8 characters returns 400`() {
        val result = doPost(
            "/api/auth/register",
            body = RegisterBody(email = "short@test.com", password = "abc", timezone = "America/Los_Angeles"),
        )
        assertThat(result.response.status).isEqualTo(400)
    }

    @Test
    fun `invalid email format returns 400`() {
        val result = doPost(
            "/api/auth/register",
            body = RegisterBody(email = "notanemail", password = "password1", timezone = "America/Los_Angeles"),
        )
        assertThat(result.response.status).isEqualTo(400)
    }

    // --- login: email not verified ---

    @Test
    fun `login with correct credentials on unverified account returns 401 EMAIL_NOT_VERIFIED`() {
        doPost(
            "/api/auth/register",
            body = RegisterBody(email = "user@test.com", password = "password1", timezone = "America/Los_Angeles"),
        )
        val result = doPost("/api/auth/login", body = LoginBody(email = "user@test.com", password = "password1"))
        assertThat(result.response.status).isEqualTo(401)
        assertThat(result.response.contentAsString).contains("EMAIL_NOT_VERIFIED")
        assertThat(mapper.readTree(result.response.contentAsString).get("verificationId").asText()).isNotBlank()
    }

    @Test
    fun `login on unverified account does not create a session`() {
        doPost(
            "/api/auth/register",
            body = RegisterBody(email = "user@test.com", password = "password1", timezone = "America/Los_Angeles"),
        )
        val result = doPost("/api/auth/login", body = LoginBody(email = "user@test.com", password = "password1"))
        assertThat(result.response.getCookie("SESSION")).isNull()
    }

    // --- login: verified ---

    @Test
    fun `successful login returns 200 and sets SESSION cookie`() {
        val verificationId = registerOnly()
        verifyLatestEmail(verificationId)
        val result = doPost("/api/auth/login", body = LoginBody(email = "user@test.com", password = "password1"))
        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("\"verificationStatus\":\"VERIFIED\"")
        assertThat(result.response.getCookie("SESSION")).isNotNull()
    }

    @Test
    fun `wrong password returns 401`() {
        doPost(
            "/api/auth/register",
            body = RegisterBody(email = "user@test.com", password = "password1", timezone = "America/Los_Angeles"),
        )
        assertThat(
            doPost("/api/auth/login", body = LoginBody(email = "user@test.com", password = "wrongpassword")).response.status,
        ).isEqualTo(401)
    }

    @Test
    fun `login with rememberMe true sets remember-me cookie`() {
        val verificationId = registerOnly()
        verifyLatestEmail(verificationId)
        val result = doPost("/api/auth/login", body = LoginBody(email = "user@test.com", password = "password1", rememberMe = true))
        assertThat(result.response.getCookie("remember-me")).isNotNull()
    }

    @Test
    fun `login with rememberMe false does not set remember-me cookie`() {
        val verificationId = registerOnly()
        verifyLatestEmail(verificationId)
        val result = doPost("/api/auth/login", body = LoginBody(email = "user@test.com", password = "password1", rememberMe = false))
        assertThat(result.response.getCookie("remember-me")).isNull()
    }

    // --- GET /auth/me ---

    @Test
    fun `unauthenticated GET me returns 401 with JSON content type`() {
        mockMvc
            .perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
    }

    @Test
    fun `authenticated GET me returns 200 with email and timezone`() {
        val session = registerAndLogin("user@test.com", "password1")
        val result = mockMvc.perform(get("/api/auth/me").cookie(session)).andReturn()
        assertThat(result.response.status).isEqualTo(200)
        val tree = mapper.readTree(result.response.contentAsString)
        assertThat(tree.get("email").asText()).isEqualTo("user@test.com")
        assertThat(tree.get("timezone").asText()).isEqualTo("America/Los_Angeles")
        assertThat(tree.has("id")).isTrue()
    }

    // --- logout ---

    @Test
    fun `after logout session cookie no longer grants access`() {
        val session = registerAndLogin()
        doPost("/api/auth/logout", session)
        val status = mockMvc
            .perform(get("/api/auth/me").cookie(session))
            .andReturn()
            .response.status
        assertThat(status).isIn(401, 403)
    }

    // --- PATCH /auth/me ---

    @Test
    fun `unauthenticated PATCH me returns 401`() {
        assertThat(
            doPatch("/api/auth/me", body = UpdateMeBody(timezone = "America/New_York")).response.status,
        ).isEqualTo(401)
    }

    @Test
    fun `PATCH me with new timezone updates timezone and returns 200`() {
        val session = registerAndLogin()
        val result = doPatch("/api/auth/me", session, UpdateMeBody(timezone = "America/New_York"))
        assertThat(result.response.status).isEqualTo(200)
        assertThat(
            mapper.readTree(result.response.contentAsString).get("timezone").asText(),
        ).isEqualTo("America/New_York")
    }

    @Test
    fun `PATCH me with null timezone leaves timezone unchanged`() {
        val session = registerAndLogin()
        val result = doPatch("/api/auth/me", session, UpdateMeBody(timezone = null))
        assertThat(result.response.status).isEqualTo(200)
        assertThat(
            mapper.readTree(result.response.contentAsString).get("timezone").asText(),
        ).isEqualTo("America/Los_Angeles")
    }

    @Test
    fun `PATCH me persists change visible on subsequent GET me`() {
        val session = registerAndLogin()
        doPatch("/api/auth/me", session, UpdateMeBody(timezone = "Europe/London"))
        val getResult = mockMvc.perform(get("/api/auth/me").cookie(session)).andReturn()
        assertThat(
            mapper.readTree(getResult.response.contentAsString).get("timezone").asText(),
        ).isEqualTo("Europe/London")
    }
}
