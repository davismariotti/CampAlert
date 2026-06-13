package com.davismariotti.campalert.security

import com.davismariotti.campalert.support.IntegrationTestBase
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class AuthIntegrationTest : IntegrationTestBase() {
    private fun register(
        email: String = "user@test.com",
        password: String = "password1",
        timezone: String = "America/Los_Angeles"
    ): MvcResult {
        val csrf = getCsrfToken()
        return mockMvc.perform(
            post("/api/auth/register")
                .cookie(Cookie("XSRF-TOKEN", csrf))
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$password","timezone":"$timezone"}""")
        ).andReturn()
    }

    private fun login(
        email: String = "user@test.com",
        password: String = "password1",
        rememberMe: Boolean? = null
    ): MvcResult {
        val csrf = getCsrfToken()
        val rememberMeJson = if (rememberMe != null) ""","rememberMe":$rememberMe""" else ""
        return mockMvc.perform(
            post("/api/auth/login")
                .cookie(Cookie("XSRF-TOKEN", csrf))
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$password"$rememberMeJson}""")
        ).andReturn()
    }

    @Test
    fun `successful registration returns 201 with email in body`() {
        val result = register()
        assertThat(result.response.status).isEqualTo(201)
        assertThat(result.response.contentAsString).contains("user@test.com")
    }

    @Test
    fun `duplicate email registration returns 409`() {
        register()
        val second = register()
        assertThat(second.response.status).isEqualTo(409)
    }

    @Test
    fun `password shorter than 8 characters returns 400`() {
        val csrf = getCsrfToken()
        val result = mockMvc.perform(
            post("/api/auth/register")
                .cookie(Cookie("XSRF-TOKEN", csrf))
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"short@test.com","password":"abc","timezone":"America/Los_Angeles"}""")
        ).andReturn()
        assertThat(result.response.status).isEqualTo(400)
    }

    @Test
    fun `successful login returns 200 and sets SESSION cookie`() {
        register()
        val result = login()
        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.getCookie("SESSION")).isNotNull()
    }

    @Test
    fun `wrong password returns 401`() {
        register()
        val result = login(password = "wrongpassword")
        assertThat(result.response.status).isEqualTo(401)
    }

    @Test
    fun `authenticated GET me with session cookie returns 200`() {
        register()
        val loginResult = login()
        val sessionCookie = loginResult.response.getCookie("SESSION")!!

        mockMvc.perform(
            get("/api/auth/me").cookie(sessionCookie)
        ).andExpect(status().isOk)
    }

    @Test
    fun `after logout session cookie no longer grants access`() {
        register()
        val loginResult = login()
        val sessionCookie = loginResult.response.getCookie("SESSION")!!
        val csrf = getCsrfToken()

        mockMvc.perform(
            post("/api/auth/logout")
                .cookie(sessionCookie)
                .cookie(Cookie("XSRF-TOKEN", csrf))
                .header("X-XSRF-TOKEN", csrf)
        ).andExpect(status().isNoContent)

        val status = mockMvc.perform(
            get("/api/auth/me").cookie(sessionCookie)
        ).andReturn().response.status
        assertThat(status).isIn(401, 403)
    }

    @Test
    fun `login with rememberMe true sets remember-me cookie`() {
        register()
        val result = login(rememberMe = true)
        assertThat(result.response.getCookie("remember-me")).isNotNull()
    }

    @Test
    fun `login with rememberMe false does not set remember-me cookie`() {
        register()
        val result = login(rememberMe = false)
        assertThat(result.response.getCookie("remember-me")).isNull()
    }

    @Test
    fun `unauthenticated GET me returns 401 with JSON content type`() {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
    }
}
