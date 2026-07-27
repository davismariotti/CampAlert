package com.davismariotti.campalert.admin

import com.davismariotti.campalert.api.model.AdminSetProviderAccessBody
import com.davismariotti.campalert.api.model.AdminSetQuotaBody
import com.davismariotti.campalert.api.model.CreateSearchRequestBody
import com.davismariotti.campalert.api.model.Provider
import com.davismariotti.campalert.api.model.ProviderType
import com.davismariotti.campalert.model.Group
import com.davismariotti.campalert.model.GroupQuotaDefault
import com.davismariotti.campalert.model.PhoneNumber
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.SearchRequestState
import com.davismariotti.campalert.provider.recreation.RidbFacility
import com.davismariotti.campalert.provider.recreation.RidbFacilityResponse
import com.davismariotti.campalert.repository.GroupQuotaDefaultRepository
import com.davismariotti.campalert.repository.GroupRepository
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import retrofit2.Call
import retrofit2.Response
import java.time.Instant
import java.time.LocalDate

class AdminIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var phoneNumberRepository: PhoneNumberRepository

    @Autowired
    private lateinit var searchRequestRepository: SearchRequestRepository

    @Autowired
    private lateinit var groupRepository: GroupRepository

    @Autowired
    private lateinit var groupQuotaDefaultRepository: GroupQuotaDefaultRepository

    @BeforeEach
    fun stubRidb() {
        val noCoordFacility = RidbFacilityResponse(
            recdata = RidbFacility(
                facilityId = "1",
                facilityName = "Test Campground",
                facilityTypeDescription = "Campground",
                parentRecAreaId = null,
                facilityLatitude = 0.0,
                facilityLongitude = 0.0,
            ),
        )

        @Suppress("UNCHECKED_CAST")
        val call = Mockito.mock(Call::class.java) as Call<RidbFacilityResponse>
        Mockito.doReturn(Response.success(noCoordFacility)).`when`(call).execute()
        Mockito.`when`(ridbApi.getFacility(anyInt())).thenReturn(call)
    }

    private fun seedVerifiedPhone(userId: Long) {
        phoneNumberRepository.save(
            PhoneNumber(userId = userId, phone = "+12125550100", status = PhoneNumberStatus.VERIFIED, smsConsentAt = Instant.now()),
        )
    }

    private fun seedActiveRequest(userId: Long, ageMinutesAgo: Long = 0): SearchRequest {
        val req = SearchRequest(
            startDay = LocalDate.now().plusDays(30),
            nights = 2,
            groupSize = 2,
            campsiteId = 100,
            name = "req",
            userId = userId,
            createdAt = Instant.now().minusSeconds(ageMinutesAgo * 60),
        )
        val state = SearchRequestState()
        state.searchRequest = req
        req.state = state
        return searchRequestRepository.save(req)
    }

    private val defaultCreateBody = CreateSearchRequestBody(
        startDay = LocalDate.of(2027, 7, 1),
        nights = 2,
        groupSize = 4,
        campsiteId = 10,
        campgroundName = "Pine Valley",
        name = "Weekend Trip",
        turnstileToken = "test-token",
    )

    // --- 16.3: admin endpoints require their specific permission ---

    @Test
    fun `GET admin stats as a non-admin returns 403`() {
        val session = registerAndLogin()
        assertThat(
            mockMvc
                .perform(get("/api/admin/stats").cookie(session))
                .andReturn()
                .response.status
        ).isEqualTo(403)
    }

    @Test
    fun `GET admin stats as an admin returns 200`() {
        val session = registerAndLoginAsAdmin()
        assertThat(
            mockMvc
                .perform(get("/api/admin/stats").cookie(session))
                .andReturn()
                .response.status
        ).isEqualTo(200)
    }

    @Test
    fun `GET admin users as a non-admin returns 403`() {
        val session = registerAndLogin()
        assertThat(
            mockMvc
                .perform(get("/api/admin/users").cookie(session))
                .andReturn()
                .response.status
        ).isEqualTo(403)
    }

    @Test
    fun `GET admin users with no query param succeeds`() {
        // Regression test: the searchUsers JPQL used to bind a null :query parameter into both an
        // IS NULL check and a LIKE/CONCAT expression, which Hibernate/Postgres mis-typed as bytea
        // and failed with "function lower(bytea) does not exist" on every unfiltered admin user search.
        val session = registerAndLoginAsAdmin()
        val result = mockMvc.perform(get("/api/admin/users").cookie(session)).andReturn()
        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("admin@test.com")
    }

    @Test
    fun `GET admin users with a query param filters by email`() {
        val session = registerAndLoginAsAdmin()
        registerAndLogin(email = "someoneelse@test.com")
        val result = mockMvc.perform(get("/api/admin/users?query=someoneelse").cookie(session)).andReturn()
        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("someoneelse@test.com")
        assertThat(result.response.contentAsString).doesNotContain("admin@test.com")
    }

    @Test
    fun `GET admin global-settings as a non-admin returns 403`() {
        val session = registerAndLogin()
        assertThat(
            mockMvc
                .perform(get("/api/admin/global-settings").cookie(session))
                .andReturn()
                .response.status
        ).isEqualTo(403)
    }

    @Test
    fun `GET admin global-settings as an admin returns 200`() {
        val session = registerAndLoginAsAdmin()
        assertThat(
            mockMvc
                .perform(get("/api/admin/global-settings").cookie(session))
                .andReturn()
                .response.status
        ).isEqualTo(200)
    }

    @Test
    fun `GET admin audit-log as a non-admin returns 403`() {
        val session = registerAndLogin()
        assertThat(
            mockMvc
                .perform(get("/api/admin/audit-log").cookie(session))
                .andReturn()
                .response.status
        ).isEqualTo(403)
    }

    @Test
    fun `GET admin audit-log as an admin returns 200`() {
        val session = registerAndLoginAsAdmin()
        assertThat(
            mockMvc
                .perform(get("/api/admin/audit-log").cookie(session))
                .andReturn()
                .response.status
        ).isEqualTo(200)
    }

    @Test
    fun `GET a users search requests as a non-admin returns 403`() {
        val adminlessSession = registerAndLogin()
        val targetUserId = userRepository.findByEmail("user@test.com")!!.id!!
        assertThat(
            mockMvc
                .perform(get("/api/admin/users/$targetUserId/search-requests").cookie(adminlessSession))
                .andReturn()
                .response.status,
        ).isEqualTo(403)
    }

    @Test
    fun `GET a users search requests as an admin returns 200`() {
        val adminSession = registerAndLoginAsAdmin()
        registerAndLogin(email = "target@test.com")
        val targetUserId = userRepository.findByEmail("target@test.com")!!.id!!
        assertThat(
            mockMvc
                .perform(get("/api/admin/users/$targetUserId/search-requests").cookie(adminSession))
                .andReturn()
                .response.status,
        ).isEqualTo(200)
    }

    // --- 16.3: quota/provider-access rejection at creation time ---

    @Test
    fun `create against a provider disabled by default (ReserveCalifornia) returns 422 PROVIDER_NOT_ALLOWED`() {
        val session = registerAndLogin()
        seedVerifiedPhone(userRepository.findByEmail("user@test.com")!!.id!!)
        val body = defaultCreateBody.copy(provider = Provider(type = ProviderType.RESERVE_CALIFORNIA, name = "ReserveCalifornia"))
        val result = doPost("/api/search-requests", session, body)
        assertThat(result.response.status).isEqualTo(422)
        assertThat(result.response.contentAsString).contains("PROVIDER_NOT_ALLOWED")
    }

    @Test
    fun `create while over the admin-set combined quota returns 422 QUOTA_EXCEEDED`() {
        val session = registerAndLogin()
        val userId = userRepository.findByEmail("user@test.com")!!.id!!
        seedVerifiedPhone(userId)

        val adminSession = registerAndLoginAsAdmin()
        assertThat(doPut("/api/admin/users/$userId/quota", adminSession, AdminSetQuotaBody(maxActive = 0)).response.status).isEqualTo(204)

        val result = doPost("/api/search-requests", session, defaultCreateBody)
        assertThat(result.response.status).isEqualTo(422)
        assertThat(result.response.contentAsString).contains("QUOTA_EXCEEDED")
    }

    // --- 16.6: eager reconciliation on direct per-user admin actions ---

    @Test
    fun `setting a per-user quota override synchronously pauses requests over the new limit`() {
        val userId = userRepository.findByEmail("user@test.com")?.id ?: run {
            registerAndLogin()
            userRepository.findByEmail("user@test.com")!!.id!!
        }
        val oldest = seedActiveRequest(userId, ageMinutesAgo = 20)
        val newest = seedActiveRequest(userId, ageMinutesAgo = 5)

        val adminSession = registerAndLoginAsAdmin()
        assertThat(doPut("/api/admin/users/$userId/quota", adminSession, AdminSetQuotaBody(maxActive = 1)).response.status).isEqualTo(204)

        assertThat(
            searchRequestRepository
                .findById(oldest.id!!)
                .get()
                .state.pauseReason
        ).isNull()
        assertThat(
            searchRequestRepository
                .findById(newest.id!!)
                .get()
                .state.pauseReason
        ).isEqualTo("QUOTA_EXCEEDED")
    }

    @Test
    fun `clearing a per-user quota override synchronously resumes requests that fit again`() {
        val userId = userRepository.findByEmail("user@test.com")?.id ?: run {
            registerAndLogin()
            userRepository.findByEmail("user@test.com")!!.id!!
        }
        val request = seedActiveRequest(userId)
        val adminSession = registerAndLoginAsAdmin()
        doPut("/api/admin/users/$userId/quota", adminSession, AdminSetQuotaBody(maxActive = 0))
        assertThat(
            searchRequestRepository
                .findById(request.id!!)
                .get()
                .state.pauseReason
        ).isEqualTo("QUOTA_EXCEEDED")

        assertThat(doDelete("/api/admin/users/$userId/quota", adminSession).response.status).isEqualTo(204)

        assertThat(
            searchRequestRepository
                .findById(request.id!!)
                .get()
                .state.pauseReason
        ).isNull()
    }

    @Test
    fun `adding a user to a group with a restrictive quota default synchronously pauses requests over that limit`() {
        registerAndLogin()
        val userId = userRepository.findByEmail("user@test.com")!!.id!!
        val oldest = seedActiveRequest(userId, ageMinutesAgo = 20)
        val newest = seedActiveRequest(userId, ageMinutesAgo = 5)

        val restrictedGroup = groupRepository.save(Group(groupName = "Restricted"))
        groupQuotaDefaultRepository.save(GroupQuotaDefault(groupId = restrictedGroup.id!!, maxActive = 1))

        val adminSession = registerAndLoginAsAdmin()
        assertThat(
            doPut("/api/admin/users/$userId/groups/${restrictedGroup.id}", adminSession).response.status,
        ).isEqualTo(204)

        assertThat(
            searchRequestRepository
                .findById(oldest.id!!)
                .get()
                .state.pauseReason
        ).isNull()
        assertThat(
            searchRequestRepository
                .findById(newest.id!!)
                .get()
                .state.pauseReason
        ).isEqualTo("QUOTA_EXCEEDED")
    }

    @Test
    fun `removing a user from a group with a restrictive quota default synchronously resumes requests`() {
        registerAndLogin()
        val userId = userRepository.findByEmail("user@test.com")!!.id!!
        val restrictedGroup = groupRepository.save(Group(groupName = "Restricted"))
        groupQuotaDefaultRepository.save(GroupQuotaDefault(groupId = restrictedGroup.id!!, maxActive = 1))
        val oldest = seedActiveRequest(userId, ageMinutesAgo = 20)
        val newest = seedActiveRequest(userId, ageMinutesAgo = 5)

        val adminSession = registerAndLoginAsAdmin()
        doPut("/api/admin/users/$userId/groups/${restrictedGroup.id}", adminSession)
        assertThat(
            searchRequestRepository
                .findById(newest.id!!)
                .get()
                .state.pauseReason
        ).isEqualTo("QUOTA_EXCEEDED")

        assertThat(
            doDelete("/api/admin/users/$userId/groups/${restrictedGroup.id}", adminSession).response.status,
        ).isEqualTo(204)

        // Back to the global default of 5, well above the 2 active requests - both resume.
        assertThat(
            searchRequestRepository
                .findById(oldest.id!!)
                .get()
                .state.pauseReason
        ).isNull()
        assertThat(
            searchRequestRepository
                .findById(newest.id!!)
                .get()
                .state.pauseReason
        ).isNull()
    }

    @Test
    fun `changing the global combined quota default does not eagerly pause anyone`() {
        registerAndLogin()
        val userId = userRepository.findByEmail("user@test.com")!!.id!!
        val request = seedActiveRequest(userId)

        val adminSession = registerAndLoginAsAdmin()
        assertThat(doPut("/api/admin/global-settings/quota", adminSession, AdminSetQuotaBody(maxActive = 0)).response.status).isEqualTo(204)

        // D4: global/group default changes are backstopped by the poll-cycle guard only, not eager
        // reconciliation - the already-active request must NOT be paused by this write alone.
        assertThat(
            searchRequestRepository
                .findById(request.id!!)
                .get()
                .state.pauseReason
        ).isNull()
    }

    @Test
    fun `disallowing a provider globally does not eagerly pause anyone`() {
        registerAndLogin()
        val userId = userRepository.findByEmail("user@test.com")!!.id!!
        val request = seedActiveRequest(userId)

        val adminSession = registerAndLoginAsAdmin()
        assertThat(
            doPut(
                "/api/admin/global-settings/providers/RECREATION_GOV/access",
                adminSession,
                AdminSetProviderAccessBody(enabled = false),
            ).response.status,
        ).isEqualTo(204)

        assertThat(
            searchRequestRepository
                .findById(request.id!!)
                .get()
                .state.pauseReason
        ).isNull()
    }
}
