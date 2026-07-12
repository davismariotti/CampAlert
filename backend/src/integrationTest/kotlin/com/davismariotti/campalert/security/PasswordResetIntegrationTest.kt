package com.davismariotti.campalert.security

import com.davismariotti.campalert.api.model.ForgotPasswordBody
import com.davismariotti.campalert.api.model.LoginBody
import com.davismariotti.campalert.api.model.ResetPasswordBody
import com.davismariotti.campalert.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import java.util.UUID

class PasswordResetIntegrationTest : IntegrationTestBase() {
    private fun registerVerifiedUser(email: String = "user@test.com", password: String = "password1") {
        val verificationId = registerOnly(email, password)
        verifyLatestEmail(verificationId)
    }

    private fun requestReset(email: String = "user@test.com"): Pair<UUID, String> {
        doPost("/api/auth/forgot-password", body = ForgotPasswordBody(email = email))
        val resetUrl = latestEmailVar("resetUrl") as String
        val resetId = UUID.fromString(resetUrl.substringAfter("resetId=").substringBefore("&"))
        val token = resetUrl.substringAfter("&token=")
        return resetId to token
    }

    // --- POST /auth/forgot-password ---

    @Test
    fun `forgot-password for an existing verified account returns 202 and emails a single-use reset link`() {
        registerVerifiedUser()
        val result = doPost("/api/auth/forgot-password", body = ForgotPasswordBody(email = "user@test.com"))

        assertThat(result.response.status).isEqualTo(202)
        val resetUrl = latestEmailVar("resetUrl") as String
        assertThat(resetUrl).contains("resetId=").contains("&token=")
    }

    @Test
    fun `forgot-password for a nonexistent account returns 202 without sending an email`() {
        val result = doPost("/api/auth/forgot-password", body = ForgotPasswordBody(email = "ghost@test.com"))

        assertThat(result.response.status).isEqualTo(202)
        assertThat(sentEmailVarsList).isEmpty()
    }

    @Test
    fun `repeated forgot-password request within the cooldown still returns 202`() {
        registerVerifiedUser()
        doPost("/api/auth/forgot-password", body = ForgotPasswordBody(email = "user@test.com"))
        waitForEmailTemplate("email/reset-password")

        val second = doPost("/api/auth/forgot-password", body = ForgotPasswordBody(email = "user@test.com"))

        assertThat(second.response.status).isEqualTo(202)
    }

    @Test
    fun `forgot-password requests within the per-IP rate limit all return 202`() {
        // application-integrationtest.properties caps this at 3 requests per 1s window
        repeat(3) {
            val result = doPost("/api/auth/forgot-password", body = ForgotPasswordBody(email = "ghost$it@test.com"))
            assertThat(result.response.status).isEqualTo(202)
        }
    }

    @Test
    fun `forgot-password requests beyond the per-IP rate limit return 429`() {
        repeat(3) { doPost("/api/auth/forgot-password", body = ForgotPasswordBody(email = "ghost$it@test.com")) }

        val fourth = doPost("/api/auth/forgot-password", body = ForgotPasswordBody(email = "onemore@test.com"))

        assertThat(fourth.response.status).isEqualTo(429)
    }

    @Test
    fun `forgot-password rate limit resets after the window elapses`() {
        repeat(3) { doPost("/api/auth/forgot-password", body = ForgotPasswordBody(email = "ghost$it@test.com")) }
        assertThat(doPost("/api/auth/forgot-password", body = ForgotPasswordBody(email = "blocked@test.com")).response.status)
            .isEqualTo(429)

        Thread.sleep(1200) // window is 1s in application-integrationtest.properties

        val afterWindow = doPost("/api/auth/forgot-password", body = ForgotPasswordBody(email = "afterwindow@test.com"))
        assertThat(afterWindow.response.status).isEqualTo(202)
    }

    // --- POST /auth/reset-password ---

    @Test
    fun `successful reset-password returns 204`() {
        registerVerifiedUser()
        val (resetId, token) = requestReset()

        val result = doPost(
            "/api/auth/reset-password",
            body = ResetPasswordBody(resetId = resetId, token = token, newPassword = "newpassword1"),
        )

        assertThat(result.response.status).isEqualTo(204)
    }

    @Test
    fun `after reset-password, old password no longer logs in and new password does`() {
        registerVerifiedUser()
        val (resetId, token) = requestReset()
        doPost("/api/auth/reset-password", body = ResetPasswordBody(resetId = resetId, token = token, newPassword = "newpassword1"))

        val oldLogin = doPost("/api/auth/login", body = LoginBody(email = "user@test.com", password = "password1"))
        assertThat(oldLogin.response.status).isEqualTo(401)

        val newLogin = doPost("/api/auth/login", body = LoginBody(email = "user@test.com", password = "newpassword1"))
        assertThat(newLogin.response.status).isEqualTo(200)
    }

    @Test
    fun `reset-password with unknown resetId returns 422 RESET_INVALID_OR_EXPIRED`() {
        registerVerifiedUser()
        val result = doPost(
            "/api/auth/reset-password",
            body = ResetPasswordBody(resetId = UUID.randomUUID(), token = "a".repeat(64), newPassword = "newpassword1"),
        )

        assertThat(result.response.status).isEqualTo(422)
        assertThat(result.response.contentAsString).contains("RESET_INVALID_OR_EXPIRED")
    }

    @Test
    fun `reset-password with wrong token returns 422 RESET_INVALID_OR_EXPIRED`() {
        registerVerifiedUser()
        val (resetId, _) = requestReset()

        val result = doPost(
            "/api/auth/reset-password",
            body = ResetPasswordBody(resetId = resetId, token = "b".repeat(64), newPassword = "newpassword1"),
        )

        assertThat(result.response.status).isEqualTo(422)
        assertThat(result.response.contentAsString).contains("RESET_INVALID_OR_EXPIRED")
    }

    @Test
    fun `reset-password with new password same as current returns 422 RESET_PASSWORD_SAME_AS_CURRENT`() {
        registerVerifiedUser()
        val (resetId, token) = requestReset()

        val result = doPost(
            "/api/auth/reset-password",
            body = ResetPasswordBody(resetId = resetId, token = token, newPassword = "password1"),
        )

        assertThat(result.response.status).isEqualTo(422)
        assertThat(result.response.contentAsString).contains("RESET_PASSWORD_SAME_AS_CURRENT")
    }

    @Test
    fun `reset-password with too-short new password returns 400 from request validation`() {
        // ResetPasswordBody.newPassword already carries @Size(min=8,max=72), so bean validation
        // rejects this with 400 before PasswordResetService's own PASSWORD_TOO_WEAK check ever runs
        // (that check remains exercised directly at the unit level in PasswordResetServiceTest).
        registerVerifiedUser()
        val (resetId, token) = requestReset()

        val result = doPost(
            "/api/auth/reset-password",
            body = ResetPasswordBody(resetId = resetId, token = token, newPassword = "short"),
        )

        assertThat(result.response.status).isEqualTo(400)
    }

    @Test
    fun `reset-password token cannot be replayed after successful consumption`() {
        registerVerifiedUser()
        val (resetId, token) = requestReset()
        doPost("/api/auth/reset-password", body = ResetPasswordBody(resetId = resetId, token = token, newPassword = "newpassword1"))

        val replay = doPost(
            "/api/auth/reset-password",
            body = ResetPasswordBody(resetId = resetId, token = token, newPassword = "anotherpassword1"),
        )

        assertThat(replay.response.status).isEqualTo(422)
        assertThat(replay.response.contentAsString).contains("RESET_INVALID_OR_EXPIRED")
    }

    @Test
    fun `successful reset-password revokes other active sessions`() {
        registerVerifiedUser()
        val existingSession = doPost("/api/auth/login", body = LoginBody(email = "user@test.com", password = "password1"))
            .response
            .getCookie("SESSION")!!

        val (resetId, token) = requestReset()
        doPost("/api/auth/reset-password", body = ResetPasswordBody(resetId = resetId, token = token, newPassword = "newpassword1"))

        val status = mockMvc
            .perform(get("/api/auth/me").cookie(existingSession))
            .andReturn()
            .response.status
        assertThat(status).isIn(401, 403)
    }

    @Test
    fun `successful reset-password revokes remember-me tokens`() {
        registerVerifiedUser()
        val rememberMeCookie = doPost(
            "/api/auth/login",
            body = LoginBody(email = "user@test.com", password = "password1", rememberMe = true),
        ).response.getCookie("remember-me")!!

        val (resetId, token) = requestReset()
        doPost("/api/auth/reset-password", body = ResetPasswordBody(resetId = resetId, token = token, newPassword = "newpassword1"))

        val status = mockMvc
            .perform(get("/api/auth/me").cookie(rememberMeCookie))
            .andReturn()
            .response.status
        assertThat(status).isIn(401, 403)
    }

    @Test
    fun `successful reset-password sends a password-changed confirmation email in addition to the reset link`() {
        registerVerifiedUser()
        val (resetId, token) = requestReset()

        doPost("/api/auth/reset-password", body = ResetPasswordBody(resetId = resetId, token = token, newPassword = "newpassword1"))

        waitForEmailTemplate("email/password-changed")
        assertThat(sentEmailTemplates).contains("email/reset-password", "email/password-changed")
    }
}
