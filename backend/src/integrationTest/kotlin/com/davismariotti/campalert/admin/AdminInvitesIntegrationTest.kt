package com.davismariotti.campalert.admin

import com.davismariotti.campalert.api.model.AdminCreateEmailInvitesBody
import com.davismariotti.campalert.api.model.AdminCreateInviteLinkBody
import com.davismariotti.campalert.api.model.AdminSetInviteOnlyBody
import com.davismariotti.campalert.api.model.RegisterBody
import com.davismariotti.campalert.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import java.util.UUID

class AdminInvitesIntegrationTest : IntegrationTestBase() {
    // ── invite-only mode toggle ──────────────────────────────────────────────

    @Test
    fun `registration-config reflects invite-only mode after an admin enables it`() {
        val adminSession = registerAndLoginAsAdmin()

        val before = mockMvc.perform(get("/api/auth/registration-config")).andReturn()
        assertThat(before.response.contentAsString).contains("\"inviteOnlyEnabled\":false")

        val setResult = doPut("/api/admin/global-settings/invite-only", adminSession, AdminSetInviteOnlyBody(enabled = true))
        assertThat(setResult.response.status).isEqualTo(204)

        val after = mockMvc.perform(get("/api/auth/registration-config")).andReturn()
        assertThat(after.response.contentAsString).contains("\"inviteOnlyEnabled\":true")
    }

    @Test
    fun `open registration is rejected once invite-only mode is enabled, but invite redemption still works`() {
        val adminSession = registerAndLoginAsAdmin()
        doPut("/api/admin/global-settings/invite-only", adminSession, AdminSetInviteOnlyBody(enabled = true))

        val openAttempt = doPost(
            "/api/auth/register",
            body = RegisterBody(email = "walkin@test.com", password = "password1", timezone = "UTC", turnstileToken = "test-token"),
        )
        assertThat(openAttempt.response.status).isEqualTo(422)
        assertThat(openAttempt.response.contentAsString).contains("INVITE_REQUIRED")

        doPost("/api/admin/invites/email", adminSession, AdminCreateEmailInvitesBody(emails = listOf("invitee@test.com")))
        val inviteUrl = latestEmailVar("inviteUrl") as String
        val inviteId = inviteUrl.substringAfter("inviteId=").substringBefore("&")
        val token = inviteUrl.substringAfter("&token=").substringBefore("&email=")

        val invitedAttempt = doPost(
            "/api/auth/register",
            body = RegisterBody(
                email = "invitee@test.com",
                password = "password1",
                timezone = "UTC",
                turnstileToken = "test-token",
                inviteId = UUID.fromString(inviteId),
                inviteToken = token,
            ),
        )
        assertThat(invitedAttempt.response.status).isEqualTo(201)
    }

    // ── permission boundary ──────────────────────────────────────────────────

    @Test
    fun `POST admin invites email as a non-admin returns 403`() {
        val session = registerAndLogin()
        val result = doPost(
            "/api/admin/invites/email",
            session,
            AdminCreateEmailInvitesBody(emails = listOf("invitee@test.com")),
        )
        assertThat(result.response.status).isEqualTo(403)
    }

    @Test
    fun `POST admin invites link as a non-admin returns 403`() {
        val session = registerAndLogin()
        val result = doPost("/api/admin/invites/link", session, AdminCreateInviteLinkBody(maxUses = 5))
        assertThat(result.response.status).isEqualTo(403)
    }

    @Test
    fun `GET admin invites as a non-admin returns 403`() {
        val session = registerAndLogin()
        assertThat(
            mockMvc
                .perform(get("/api/admin/invites").cookie(session))
                .andReturn()
                .response.status
        ).isEqualTo(403)
    }

    @Test
    fun `DELETE admin invites id as a non-admin returns 403`() {
        val session = registerAndLogin()
        val result = doDelete("/api/admin/invites/${UUID.randomUUID()}", session)
        assertThat(result.response.status).isEqualTo(403)
    }

    // ── email invite: full create -> redeem -> REDEEMED flow ────────────────

    @Test
    fun `redeeming an email invite auto-verifies and the invite becomes REDEEMED`() {
        val adminSession = registerAndLoginAsAdmin()
        doPost("/api/admin/invites/email", adminSession, AdminCreateEmailInvitesBody(emails = listOf("invitee@test.com")))

        val inviteUrl = latestEmailVar("inviteUrl") as String
        val inviteId = inviteUrl.substringAfter("inviteId=").substringBefore("&")
        val token = inviteUrl.substringAfter("&token=").substringBefore("&email=")

        val registerResult = doPost(
            "/api/auth/register",
            body = RegisterBody(
                email = "invitee@test.com",
                password = "password1",
                timezone = "America/Los_Angeles",
                turnstileToken = "test-token",
                inviteId = UUID.fromString(inviteId),
                inviteToken = token,
            ),
        )
        assertThat(registerResult.response.status).isEqualTo(201)
        assertThat(registerResult.response.contentAsString).contains("\"verificationStatus\":\"VERIFIED\"")
        assertThat(registerResult.response.getCookie("SESSION")).isNotNull()

        val listResult = mockMvc.perform(get("/api/admin/invites?includeInactive=true").cookie(adminSession)).andReturn()
        assertThat(listResult.response.contentAsString).contains("\"status\":\"REDEEMED\"")
        assertThat(listResult.response.contentAsString).contains("\"usedCount\":1")
    }

    @Test
    fun `default admin invites list excludes a redeemed invite`() {
        val adminSession = registerAndLoginAsAdmin()
        doPost("/api/admin/invites/email", adminSession, AdminCreateEmailInvitesBody(emails = listOf("invitee@test.com")))
        val inviteUrl = latestEmailVar("inviteUrl") as String
        val inviteId = inviteUrl.substringAfter("inviteId=").substringBefore("&")
        val token = inviteUrl.substringAfter("&token=").substringBefore("&email=")
        doPost(
            "/api/auth/register",
            body = RegisterBody(
                email = "invitee@test.com",
                password = "password1",
                timezone = "America/Los_Angeles",
                turnstileToken = "test-token",
                inviteId = UUID.fromString(inviteId),
                inviteToken = token,
            ),
        )

        val listResult = mockMvc.perform(get("/api/admin/invites").cookie(adminSession)).andReturn()
        assertThat(listResult.response.contentAsString).doesNotContain("invitee@test.com")
    }

    // ── deactivation blocks redemption with type-specific guidance ──────────

    @Test
    fun `redeeming a deactivated email invite tells the user to request a new invitation`() {
        val adminSession = registerAndLoginAsAdmin()
        doPost("/api/admin/invites/email", adminSession, AdminCreateEmailInvitesBody(emails = listOf("invitee@test.com")))
        val inviteUrl = latestEmailVar("inviteUrl") as String
        val inviteId = inviteUrl.substringAfter("inviteId=").substringBefore("&")
        val token = inviteUrl.substringAfter("&token=").substringBefore("&email=")

        val deactivateResult = doDelete("/api/admin/invites/$inviteId", adminSession)
        assertThat(deactivateResult.response.status).isEqualTo(204)

        val registerResult = doPost(
            "/api/auth/register",
            body = RegisterBody(
                email = "invitee@test.com",
                password = "password1",
                timezone = "America/Los_Angeles",
                turnstileToken = "test-token",
                inviteId = UUID.fromString(inviteId),
                inviteToken = token,
            ),
        )
        assertThat(registerResult.response.status).isEqualTo(422)
        assertThat(registerResult.response.contentAsString).contains("INVITE_DEACTIVATED")
        assertThat(registerResult.response.contentAsString).contains("new invitation")
    }

    @Test
    fun `deactivating an already-deactivated invite returns 409`() {
        val adminSession = registerAndLoginAsAdmin()
        val createResult = doPost("/api/admin/invites/link", adminSession, AdminCreateInviteLinkBody(maxUses = 1))
        val inviteId = mapper
            .readTree(createResult.response.contentAsString)
            .get("invite")
            .get("id")
            .asText()

        doDelete("/api/admin/invites/$inviteId", adminSession)
        val secondAttempt = doDelete("/api/admin/invites/$inviteId", adminSession)

        assertThat(secondAttempt.response.status).isEqualTo(409)
    }

    // ── expired invite: type-specific guidance for a public link ────────────

    @Test
    fun `redeeming an expired public link tells the user to request a new link`() {
        val adminSession = registerAndLoginAsAdmin()
        val createResult = doPost("/api/admin/invites/link", adminSession, AdminCreateInviteLinkBody(maxUses = 5))
        val body = mapper.readTree(createResult.response.contentAsString)
        val inviteId = body.get("invite").get("id").asText()
        val url = body.get("url").asText()
        val token = url.substringAfter("&token=").substringBefore("&email=")

        jdbcTemplate.update("UPDATE invites SET expires_at = now() - interval '1 day' WHERE id = ?", UUID.fromString(inviteId))

        val registerResult = doPost(
            "/api/auth/register",
            body = RegisterBody(
                email = "anyone@test.com",
                password = "password1",
                timezone = "America/Los_Angeles",
                turnstileToken = "test-token",
                inviteId = UUID.fromString(inviteId),
                inviteToken = token,
            ),
        )
        assertThat(registerResult.response.status).isEqualTo(422)
        assertThat(registerResult.response.contentAsString).contains("INVITE_INVALID_OR_EXPIRED")
        assertThat(registerResult.response.contentAsString).contains("new link")
    }

    // ── capacity ──────────────────────────────────────────────────────────

    @Test
    fun `a public link cannot be redeemed more than maxUses times`() {
        val adminSession = registerAndLoginAsAdmin()
        val createResult = doPost("/api/admin/invites/link", adminSession, AdminCreateInviteLinkBody(maxUses = 1))
        val body = mapper.readTree(createResult.response.contentAsString)
        val inviteId = body.get("invite").get("id").asText()
        val url = body.get("url").asText()
        val token = url.substringAfter("&token=").substringBefore("&email=")

        val first = doPost(
            "/api/auth/register",
            body = RegisterBody(
                email = "first@test.com",
                password = "password1",
                timezone = "America/Los_Angeles",
                turnstileToken = "test-token",
                inviteId = UUID.fromString(inviteId),
                inviteToken = token,
            ),
        )
        assertThat(first.response.status).isEqualTo(201)
        // Public link redemptions still require normal OTP verification (no auto-verify).
        assertThat(first.response.contentAsString).contains("\"verificationStatus\":\"PENDING_VERIFICATION\"")

        val second = doPost(
            "/api/auth/register",
            body = RegisterBody(
                email = "second@test.com",
                password = "password1",
                timezone = "America/Los_Angeles",
                turnstileToken = "test-token",
                inviteId = UUID.fromString(inviteId),
                inviteToken = token,
            ),
        )
        assertThat(second.response.status).isEqualTo(422)
        assertThat(second.response.contentAsString).contains("INVITE_EXHAUSTED")
    }
}
