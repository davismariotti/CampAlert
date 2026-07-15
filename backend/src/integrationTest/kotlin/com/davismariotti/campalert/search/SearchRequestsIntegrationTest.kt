package com.davismariotti.campalert.search

import com.davismariotti.campalert.api.model.CreateSearchRequestBody
import com.davismariotti.campalert.api.model.UpdateSearchRequestBody
import com.davismariotti.campalert.model.NotificationOutbox
import com.davismariotti.campalert.model.OutboxType
import com.davismariotti.campalert.model.PhoneNumber
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.model.RequestType
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.SearchRequestState
import com.davismariotti.campalert.provider.recreation.RidbFacility
import com.davismariotti.campalert.provider.recreation.RidbFacilityResponse
import com.davismariotti.campalert.repository.NotificationOutboxRepository
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.support.IntegrationTestBase
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import retrofit2.Call
import retrofit2.Response
import java.time.Instant
import java.time.LocalDate

class SearchRequestsIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var phoneNumberRepository: PhoneNumberRepository

    @Autowired
    private lateinit var searchRequestRepository: SearchRequestRepository

    @Autowired
    private lateinit var notificationOutboxRepository: NotificationOutboxRepository

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
            )
        )
        val defaultCall = successCall(noCoordFacility)
        Mockito.`when`(ridbApi.getFacility(anyInt())).thenReturn(defaultCall)
    }

    // --- helpers ---

    @Suppress("UNCHECKED_CAST")
    private fun <T> successCall(body: T): Call<T> {
        val call = Mockito.mock(Call::class.java) as Call<T>
        Mockito.doReturn(Response.success(body)).`when`(call).execute()
        return call
    }

    private val defaultCreateBody = CreateSearchRequestBody(
        startDay = LocalDate.of(2027, 7, 1),
        nights = 2,
        groupSize = 4,
        campsiteId = 10,
        campgroundName = "Pine Valley",
        name = "Weekend Trip",
    )

    private val defaultUpdateBody = UpdateSearchRequestBody(
        startDay = LocalDate.of(2027, 7, 1),
        nights = 2,
        groupSize = 4,
        campsiteId = 10,
        name = "Weekend Trip",
        completed = false,
    )

    private fun createRequest(session: Cookie, name: String = "Weekend Trip"): MvcResult = doPost("/api/search-requests", session, defaultCreateBody.copy(name = name))

    private fun seedVerifiedPhone(userId: Long): PhoneNumber =
        phoneNumberRepository.save(
            PhoneNumber(
                userId = userId,
                phone = "+12125550100",
                status = PhoneNumberStatus.VERIFIED,
                smsConsentAt = Instant.now(),
            )
        )

    private fun seedRequest(userId: Long, completed: Boolean = false, pauseReason: String? = null): SearchRequest {
        val req = SearchRequest(
            userId = userId,
            startDay = LocalDate.now().plusDays(30),
            nights = 2,
            groupSize = 4,
            campsiteId = 10,
            name = "Weekend Trip",
            campgroundName = "Pine Valley",
        )
        val st = SearchRequestState()
        st.searchRequest = req
        st.completed = completed
        st.pauseReason = pauseReason
        req.state = st
        return searchRequestRepository.save(req)
    }

    // --- 3.3 Unauthenticated 401 ---

    @Test
    fun `unauthenticated GET list search requests returns 401`() {
        assertThat(
            mockMvc
                .perform(get("/api/search-requests"))
                .andReturn()
                .response.status
        ).isEqualTo(401)
    }

    @Test
    fun `unauthenticated POST create search request returns 401`() {
        assertThat(doPost("/api/search-requests", body = defaultCreateBody).response.status).isEqualTo(401)
    }

    @Test
    fun `unauthenticated GET single search request returns 401`() {
        assertThat(
            mockMvc
                .perform(get("/api/search-requests/1"))
                .andReturn()
                .response.status
        ).isEqualTo(401)
    }

    @Test
    fun `unauthenticated PUT update search request returns 401`() {
        assertThat(doPut("/api/search-requests/1", body = defaultUpdateBody).response.status).isEqualTo(401)
    }

    @Test
    fun `unauthenticated DELETE search request returns 401`() {
        assertThat(doDelete("/api/search-requests/1").response.status).isEqualTo(401)
    }

    // --- 3.4 createSearchRequest no-phone gate ---

    @Test
    fun `create with no verified phone returns 422 with NO_VERIFIED_PHONE`() {
        val session = registerAndLogin()
        val result = createRequest(session)
        assertThat(result.response.status).isEqualTo(422)
        assertThat(result.response.contentAsString).contains("NO_VERIFIED_PHONE")
    }

    // --- 3.5 createSearchRequest success ---

    @Test
    fun `create with verified phone returns 201 with persisted fields`() {
        val session = registerAndLogin()
        seedVerifiedPhone(userRepository.findByEmail("user@test.com")!!.id!!)
        val result = createRequest(session)
        assertThat(result.response.status).isEqualTo(201)
        assertThat(result.response.contentAsString).contains("Weekend Trip")
        assertThat(result.response.contentAsString).contains("Pine Valley")
    }

    // --- 3.6 zero stats ---

    @Test
    fun `new search request has zero stats`() {
        val session = registerAndLogin()
        seedVerifiedPhone(userRepository.findByEmail("user@test.com")!!.id!!)
        val tree = mapper.readTree(createRequest(session).response.contentAsString)
        assertThat(tree.get("stats").get("totalChecks").asLong()).isEqualTo(0)
        assertThat(tree.get("stats").get("availableChecks").asLong()).isEqualTo(0)
        assertThat(tree.get("stats").get("availabilityRate").isNull).isTrue()
    }

    // --- 3.7 RIDB failure does not fail create ---

    @Test
    fun `RIDB failure during timezone resolution does not fail search request creation`() {
        val session = registerAndLogin()
        seedVerifiedPhone(userRepository.findByEmail("user@test.com")!!.id!!)
        Mockito.`when`(ridbApi.getFacility(anyInt())).thenThrow(RuntimeException("RIDB down"))
        assertThat(createRequest(session).response.status).isEqualTo(201)
    }

    // --- 3.8 listSearchRequests ---

    @Test
    fun `list returns empty array when user has no requests`() {
        val session = registerAndLogin()
        val result = mockMvc.perform(get("/api/search-requests").cookie(session)).andReturn()
        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo("[]")
    }

    @Test
    fun `list returns authenticated users requests`() {
        val session = registerAndLogin()
        seedRequest(userRepository.findByEmail("user@test.com")!!.id!!)
        val result = mockMvc.perform(get("/api/search-requests").cookie(session)).andReturn()
        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("Weekend Trip")
    }

    @Test
    fun `list does not return other users requests`() {
        registerAndLogin("user1@test.com")
        seedRequest(userRepository.findByEmail("user1@test.com")!!.id!!)
        val session2 = registerAndLogin("user2@test.com")
        val result = mockMvc.perform(get("/api/search-requests").cookie(session2)).andReturn()
        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo("[]")
    }

    // --- 3.9 listSearchRequests filters ---

    @Test
    fun `completed=false returns only active requests`() {
        val session = registerAndLogin()
        val userId = userRepository.findByEmail("user@test.com")!!.id!!
        seedRequest(userId, completed = false)
        seedRequest(userId, completed = true)
        val tree = mapper.readTree(
            mockMvc
                .perform(
                    get("/api/search-requests?completed=false").cookie(session)
                ).andReturn()
                .response.contentAsString
        )
        assertThat(tree.size()).isEqualTo(1)
        assertThat(tree[0].get("completed").asBoolean()).isFalse()
    }

    @Test
    fun `completed=true returns only completed requests`() {
        val session = registerAndLogin()
        val userId = userRepository.findByEmail("user@test.com")!!.id!!
        seedRequest(userId, completed = false)
        seedRequest(userId, completed = true)
        val tree = mapper.readTree(
            mockMvc
                .perform(
                    get("/api/search-requests?completed=true").cookie(session)
                ).andReturn()
                .response.contentAsString
        )
        assertThat(tree.size()).isEqualTo(1)
        assertThat(tree[0].get("completed").asBoolean()).isTrue()
    }

    @Test
    fun `omitting completed param returns all requests`() {
        val session = registerAndLogin()
        val userId = userRepository.findByEmail("user@test.com")!!.id!!
        seedRequest(userId, completed = false)
        seedRequest(userId, completed = true)
        val tree = mapper.readTree(
            mockMvc
                .perform(get("/api/search-requests").cookie(session))
                .andReturn()
                .response.contentAsString
        )
        assertThat(tree.size()).isEqualTo(2)
    }

    // --- 3.10 getSearchRequest ---

    @Test
    fun `get nonexistent search request returns 404`() {
        val session = registerAndLogin()
        assertThat(
            mockMvc
                .perform(get("/api/search-requests/9999").cookie(session))
                .andReturn()
                .response.status
        ).isEqualTo(404)
    }

    @Test
    fun `get search request belonging to another user returns 404`() {
        registerAndLogin("user1@test.com")
        val request = seedRequest(userRepository.findByEmail("user1@test.com")!!.id!!)
        val session2 = registerAndLogin("user2@test.com")
        assertThat(
            mockMvc
                .perform(get("/api/search-requests/${request.id}").cookie(session2))
                .andReturn()
                .response.status
        ).isEqualTo(404)
    }

    @Test
    fun `get own search request returns 200 with correct fields`() {
        val session = registerAndLogin()
        val request = seedRequest(userRepository.findByEmail("user@test.com")!!.id!!)
        val result = mockMvc.perform(get("/api/search-requests/${request.id}").cookie(session)).andReturn()
        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("Weekend Trip")
        assertThat(result.response.contentAsString).contains("Pine Valley")
    }

    // --- 3.11 updateSearchRequest ---

    @Test
    fun `update nonexistent search request returns 404`() {
        val session = registerAndLogin()
        assertThat(doPut("/api/search-requests/9999", session, defaultUpdateBody).response.status).isEqualTo(404)
    }

    @Test
    fun `update search request belonging to another user returns 404`() {
        registerAndLogin("user1@test.com")
        val request = seedRequest(userRepository.findByEmail("user1@test.com")!!.id!!)
        val session2 = registerAndLogin("user2@test.com")
        assertThat(
            doPut("/api/search-requests/${request.id}", session2, defaultUpdateBody).response.status
        ).isEqualTo(404)
    }

    @Test
    fun `update search request persists new fields and returns 200`() {
        val session = registerAndLogin()
        val request = seedRequest(userRepository.findByEmail("user@test.com")!!.id!!)
        val body = UpdateSearchRequestBody(
            startDay = LocalDate.of(2027, 8, 15),
            nights = 3,
            groupSize = 6,
            campsiteId = 20,
            name = "Summer Trip",
            completed = false,
        )
        val result = doPut("/api/search-requests/${request.id}", session, body)
        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("Summer Trip")
        assertThat(result.response.contentAsString).contains("\"nights\":3")
    }

    @Test
    fun `can mark search request as completed via update`() {
        val session = registerAndLogin()
        val request = seedRequest(userRepository.findByEmail("user@test.com")!!.id!!, completed = false)
        val result = doPut("/api/search-requests/${request.id}", session, defaultUpdateBody.copy(completed = true))
        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("\"completed\":true")
    }

    // --- 3.12 deleteSearchRequest ---

    @Test
    fun `delete nonexistent search request returns 404`() {
        assertThat(doDelete("/api/search-requests/9999", registerAndLogin()).response.status).isEqualTo(404)
    }

    @Test
    fun `delete search request belonging to another user returns 404`() {
        registerAndLogin("user1@test.com")
        val request = seedRequest(userRepository.findByEmail("user1@test.com")!!.id!!)
        val session2 = registerAndLogin("user2@test.com")
        assertThat(doDelete("/api/search-requests/${request.id}", session2).response.status).isEqualTo(404)
    }

    @Test
    fun `delete search request returns 204 and subsequent GET returns 404`() {
        val session = registerAndLogin()
        val request = seedRequest(userRepository.findByEmail("user@test.com")!!.id!!)
        assertThat(doDelete("/api/search-requests/${request.id}", session).response.status).isEqualTo(204)
        assertThat(
            mockMvc
                .perform(get("/api/search-requests/${request.id}").cookie(session))
                .andReturn()
                .response.status
        ).isEqualTo(404)
    }

    // --- flexible search range validation ---

    @Test
    fun `create with a valid flexible range returns 201 with searchEndDay persisted`() {
        val session = registerAndLogin()
        seedVerifiedPhone(userRepository.findByEmail("user@test.com")!!.id!!)
        val result = doPost(
            "/api/search-requests",
            session,
            defaultCreateBody.copy(startDay = LocalDate.of(2027, 7, 1), nights = 2, searchEndDay = LocalDate.of(2027, 7, 10)),
        )
        assertThat(result.response.status).isEqualTo(201)
        val tree = mapper.readTree(result.response.contentAsString)
        assertThat(tree.get("searchEndDay").textValue()).isEqualTo("2027-07-10")
        assertThat(tree.get("matchedStartDay").isNull).isTrue()
    }

    @Test
    fun `create with searchEndDay narrower than startDay plus nights returns 400`() {
        val session = registerAndLogin()
        seedVerifiedPhone(userRepository.findByEmail("user@test.com")!!.id!!)
        val result = doPost(
            "/api/search-requests",
            session,
            defaultCreateBody.copy(startDay = LocalDate.of(2027, 7, 1), nights = 2, searchEndDay = LocalDate.of(2027, 7, 2)),
        )
        assertThat(result.response.status).isEqualTo(400)
        assertThat(result.response.contentAsString).contains("SEARCH_END_DAY_TOO_EARLY")
    }

    @Test
    fun `create with a range wider than the provider max returns 400`() {
        val session = registerAndLogin()
        seedVerifiedPhone(userRepository.findByEmail("user@test.com")!!.id!!)
        val result = doPost(
            "/api/search-requests",
            session,
            defaultCreateBody.copy(startDay = LocalDate.of(2027, 7, 1), nights = 2, searchEndDay = LocalDate.of(2027, 8, 15)),
        )
        assertThat(result.response.status).isEqualTo(400)
        assertThat(result.response.contentAsString).contains("SEARCH_RANGE_TOO_WIDE")
    }

    @Test
    fun `create with nights over 21 returns 400`() {
        val session = registerAndLogin()
        seedVerifiedPhone(userRepository.findByEmail("user@test.com")!!.id!!)
        val result = doPost("/api/search-requests", session, defaultCreateBody.copy(nights = 22))
        assertThat(result.response.status).isEqualTo(400)
    }

    @Test
    fun `update to clear searchEndDay reverts to an exact-date search`() {
        val session = registerAndLogin()
        val userId = userRepository.findByEmail("user@test.com")!!.id!!
        val req = SearchRequest(
            userId = userId,
            startDay = LocalDate.of(2027, 7, 1),
            nights = 2,
            groupSize = 4,
            campsiteId = 10,
            name = "Weekend Trip",
            campgroundName = "Pine Valley",
            searchEndDay = LocalDate.of(2027, 7, 10),
        )
        val st = SearchRequestState()
        st.searchRequest = req
        req.state = st
        val saved = searchRequestRepository.save(req)

        val result = doPut(
            "/api/search-requests/${saved.id}",
            session,
            defaultUpdateBody.copy(searchEndDay = null),
        )
        assertThat(result.response.status).isEqualTo(200)
        val tree = mapper.readTree(result.response.contentAsString)
        assertThat(tree.get("searchEndDay").isNull).isTrue()
    }

    @Test
    fun `delete search request removes its outbox rows`() {
        val session = registerAndLogin()
        val userId = userRepository.findByEmail("user@test.com")!!.id!!
        val request = seedRequest(userId)
        val outboxRow = notificationOutboxRepository.save(
            NotificationOutbox(
                userId = userId,
                requestId = request.id!!,
                requestType = RequestType.CAMPGROUND,
                type = OutboxType.AVAILABLE,
                sendAfter = Instant.now(),
            )
        )

        assertThat(doDelete("/api/search-requests/${request.id}", session).response.status).isEqualTo(204)

        assertThat(notificationOutboxRepository.findById(outboxRow.id!!)).isEmpty()
    }
}
