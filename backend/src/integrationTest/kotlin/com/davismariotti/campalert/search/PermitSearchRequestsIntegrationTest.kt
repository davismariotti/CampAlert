package com.davismariotti.campalert.search

import com.davismariotti.campalert.api.model.CreatePermitSearchRequestBody
import com.davismariotti.campalert.api.model.PermitItineraryLegBody
import com.davismariotti.campalert.api.model.PermitItineraryTargetBody
import com.davismariotti.campalert.api.model.PermitType
import com.davismariotti.campalert.api.model.PermitZoneTargetBody
import com.davismariotti.campalert.api.model.UpdatePermitSearchRequestBody
import com.davismariotti.campalert.model.PhoneNumber
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.recreation.PermitContentPayload
import com.davismariotti.campalert.recreation.PermitContentResponse
import com.davismariotti.campalert.recreation.PermitDivisionContent
import com.davismariotti.campalert.recreation.PermitDivisionType
import com.davismariotti.campalert.recreation.PermitMappingPayload
import com.davismariotti.campalert.recreation.PermitMappingResponse
import com.davismariotti.campalert.recreation.PermitRuleContent
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.support.IntegrationTestBase
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import retrofit2.Call
import retrofit2.Response
import java.time.Instant
import java.time.LocalDate

class PermitSearchRequestsIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var phoneNumberRepository: PhoneNumberRepository

    // --- helpers ---

    @Suppress("UNCHECKED_CAST")
    private fun <T> successCall(body: T): Call<T> {
        val call = Mockito.mock(Call::class.java) as Call<T>
        Mockito.doReturn(Response.success(body)).`when`(call).execute()
        return call
    }

    private fun stubMapping(itineraryIds: List<String> = emptyList(), dayUseIds: List<String> = emptyList()) {
        val call = successCall(PermitMappingResponse(PermitMappingPayload(itineraryPermitIds = itineraryIds, dayUsePermitIds = dayUseIds)))
        Mockito.`when`(recreationApi.getPermitMapping()).thenReturn(call)
    }

    private fun stubContent(permitId: String, divisions: Map<String, PermitDivisionContent>, rules: List<PermitRuleContent> = emptyList()) {
        val call = successCall(PermitContentResponse(PermitContentPayload(divisions = divisions, rules = rules)))
        Mockito.`when`(recreationApi.getPermitContent(permitId)).thenReturn(call)
    }

    /** Unflagged in permitmapping, with a valid "Destination Zone" structural fallback. */
    private fun stubZonePermit(permitId: String, divisionIds: List<String> = listOf("343")) {
        stubMapping()
        stubContent(
            permitId,
            divisions = divisionIds.associateWith { PermitDivisionContent(id = it, name = "Zone $it", type = PermitDivisionType.DESTINATION_ZONE) },
            rules = listOf(PermitRuleContent(operation = "FixedValueByMembersEnteringPerDay")),
        )
    }

    private fun stubItineraryPermit(permitId: String, divisionIds: Map<String, List<String>> = mapOf("A" to listOf("B"), "B" to emptyList())) {
        stubMapping(itineraryIds = listOf(permitId))
        stubContent(permitId, divisions = divisionIds.entries.associate { (id, children) -> id to PermitDivisionContent(id = id, name = "Division $id", children = children) })
    }

    private fun stubUnsupportedPermit(permitId: String) {
        stubMapping(dayUseIds = listOf(permitId))
    }

    private fun seedVerifiedPhone(userId: Long): PhoneNumber =
        phoneNumberRepository.save(
            PhoneNumber(userId = userId, phone = "+12125550100", status = PhoneNumberStatus.VERIFIED, smsConsentAt = Instant.now()),
        )

    private val zoneCreateBody = CreatePermitSearchRequestBody(
        permitId = "233261",
        permitName = "Desolation Wilderness",
        groupSize = 4,
        name = "Aloha Zone Watch",
        searchType = PermitType.ZONE,
        zoneTarget = PermitZoneTargetBody(divisionIds = listOf("343"), startDay = LocalDate.of(2027, 7, 10), endDay = LocalDate.of(2027, 7, 15)),
    )

    private val itineraryCreateBody = CreatePermitSearchRequestBody(
        permitId = "4675323",
        permitName = "Yellowstone Backcountry",
        groupSize = 2,
        name = "Blacktail Loop",
        searchType = PermitType.ITINERARY,
        itineraryTarget = PermitItineraryTargetBody(legs = listOf(PermitItineraryLegBody(divisionId = "A", date = LocalDate.of(2027, 7, 12)))),
    )

    private fun createRequest(session: Cookie, body: CreatePermitSearchRequestBody): MvcResult = doPost("/api/permit-search-requests", session, body)

    // --- unauthenticated 401 ---

    @Test
    fun `unauthenticated GET list permit search requests returns 401`() {
        assertThat(
            mockMvc
                .perform(get("/api/permit-search-requests"))
                .andReturn()
                .response.status
        ).isEqualTo(401)
    }

    @Test
    fun `unauthenticated POST create permit search request returns 401`() {
        assertThat(doPost("/api/permit-search-requests", body = zoneCreateBody).response.status).isEqualTo(401)
    }

    @Test
    fun `unauthenticated GET single permit search request returns 401`() {
        assertThat(
            mockMvc
                .perform(get("/api/permit-search-requests/1"))
                .andReturn()
                .response.status
        ).isEqualTo(401)
    }

    @Test
    fun `unauthenticated DELETE permit search request returns 401`() {
        assertThat(doDelete("/api/permit-search-requests/1").response.status).isEqualTo(401)
    }

    // --- createPermitSearchRequest ---

    @Test
    fun `create with no verified phone returns 422 with NO_VERIFIED_PHONE`() {
        val session = registerAndLogin()
        stubZonePermit("233261")
        val result = createRequest(session, zoneCreateBody)
        assertThat(result.response.status).isEqualTo(422)
        assertThat(result.response.contentAsString).contains("NO_VERIFIED_PHONE")
    }

    @Test
    fun `create zone permit with verified phone returns 201 with persisted fields`() {
        val session = registerAndLogin()
        seedVerifiedPhone(userRepository.findByEmail("user@test.com")!!.id!!)
        stubZonePermit("233261")

        val result = createRequest(session, zoneCreateBody)

        assertThat(result.response.status).isEqualTo(201)
        assertThat(result.response.contentAsString).contains("Aloha Zone Watch")
        assertThat(result.response.contentAsString).contains("\"searchType\":\"ZONE\"")
        assertThat(result.response.contentAsString).contains("\"divisionIds\":[\"343\"]")
    }

    @Test
    fun `create itinerary permit with verified phone returns 201 with persisted legs`() {
        val session = registerAndLogin()
        seedVerifiedPhone(userRepository.findByEmail("user@test.com")!!.id!!)
        stubItineraryPermit("4675323")

        val result = createRequest(session, itineraryCreateBody)

        assertThat(result.response.status).isEqualTo(201)
        assertThat(result.response.contentAsString).contains("Blacktail Loop")
        assertThat(result.response.contentAsString).contains("\"searchType\":\"ITINERARY\"")
    }

    @Test
    fun `create against unsupported permit returns 422 PERMIT_TYPE_NOT_SUPPORTED`() {
        val session = registerAndLogin()
        seedVerifiedPhone(userRepository.findByEmail("user@test.com")!!.id!!)
        stubUnsupportedPermit("233261")

        val result = createRequest(session, zoneCreateBody)

        assertThat(result.response.status).isEqualTo(422)
        assertThat(result.response.contentAsString).contains("PERMIT_TYPE_NOT_SUPPORTED")
    }

    @Test
    fun `create with searchType not matching the permits actual classification returns 422 PERMIT_TYPE_MISMATCH`() {
        val session = registerAndLogin()
        seedVerifiedPhone(userRepository.findByEmail("user@test.com")!!.id!!)
        // permit is actually ITINERARY, but the body claims ZONE
        stubItineraryPermit("4675323")
        val body = zoneCreateBody.copy(permitId = "4675323", permitName = "Yellowstone Backcountry")

        val result = createRequest(session, body)

        assertThat(result.response.status).isEqualTo(422)
        assertThat(result.response.contentAsString).contains("PERMIT_TYPE_MISMATCH")
    }

    @Test
    fun `create itinerary with illegal leg sequence returns 422 ILLEGAL_LEG_SEQUENCE`() {
        val session = registerAndLogin()
        seedVerifiedPhone(userRepository.findByEmail("user@test.com")!!.id!!)
        // "A"'s only legal child is "B" — jumping straight to "C" is illegal
        stubItineraryPermit("4675323", divisionIds = mapOf("A" to listOf("B"), "B" to emptyList(), "C" to emptyList()))
        val body = itineraryCreateBody.copy(
            itineraryTarget = PermitItineraryTargetBody(
                legs = listOf(
                    PermitItineraryLegBody(divisionId = "A", date = LocalDate.of(2027, 7, 12)),
                    PermitItineraryLegBody(divisionId = "C", date = LocalDate.of(2027, 7, 13)),
                ),
            ),
        )

        val result = createRequest(session, body)

        assertThat(result.response.status).isEqualTo(422)
        assertThat(result.response.contentAsString).contains("ILLEGAL_LEG_SEQUENCE")
    }

    @Test
    fun `create zone body with mismatched target shape returns 400`() {
        val session = registerAndLogin()
        seedVerifiedPhone(userRepository.findByEmail("user@test.com")!!.id!!)
        stubZonePermit("233261")
        val body = zoneCreateBody.copy(itineraryTarget = PermitItineraryTargetBody(legs = listOf(PermitItineraryLegBody(divisionId = "A", date = LocalDate.of(2027, 7, 12)))))

        val result = createRequest(session, body)

        assertThat(result.response.status).isEqualTo(400)
    }

    // --- listPermitSearchRequests / getPermitSearchRequest ---

    @Test
    fun `list returns empty array when user has no requests`() {
        val session = registerAndLogin()
        val result = mockMvc.perform(get("/api/permit-search-requests").cookie(session)).andReturn()
        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo("[]")
    }

    @Test
    fun `list returns authenticated users requests`() {
        val session = registerAndLogin()
        seedVerifiedPhone(userRepository.findByEmail("user@test.com")!!.id!!)
        stubZonePermit("233261")
        createRequest(session, zoneCreateBody)

        val result = mockMvc.perform(get("/api/permit-search-requests").cookie(session)).andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("Aloha Zone Watch")
    }

    @Test
    fun `get nonexistent permit search request returns 404`() {
        val session = registerAndLogin()
        assertThat(
            mockMvc
                .perform(get("/api/permit-search-requests/9999").cookie(session))
                .andReturn()
                .response.status
        ).isEqualTo(404)
    }

    @Test
    fun `get permit search request belonging to another user returns 404`() {
        val session1 = registerAndLogin("user1@test.com")
        seedVerifiedPhone(userRepository.findByEmail("user1@test.com")!!.id!!)
        stubZonePermit("233261")
        val created = createRequest(session1, zoneCreateBody)
        val id = extractId(created)

        val session2 = registerAndLogin("user2@test.com")
        assertThat(
            mockMvc
                .perform(get("/api/permit-search-requests/$id").cookie(session2))
                .andReturn()
                .response.status
        ).isEqualTo(404)
    }

    // --- updatePermitSearchRequest ---

    @Test
    fun `update persists new group size and returns 200`() {
        val session = registerAndLogin()
        seedVerifiedPhone(userRepository.findByEmail("user@test.com")!!.id!!)
        stubZonePermit("233261")
        val created = createRequest(session, zoneCreateBody)
        val id = extractId(created)

        val updateBody = UpdatePermitSearchRequestBody(
            permitId = "233261",
            permitName = "Desolation Wilderness",
            groupSize = 6,
            name = "Aloha Zone Watch",
            searchType = PermitType.ZONE,
            completed = false,
            zoneTarget = PermitZoneTargetBody(divisionIds = listOf("343"), startDay = LocalDate.of(2027, 7, 10), endDay = LocalDate.of(2027, 7, 20)),
        )
        val result = doPut("/api/permit-search-requests/$id", session, updateBody)

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("\"groupSize\":6")
    }

    @Test
    fun `update nonexistent permit search request returns 404`() {
        val session = registerAndLogin()
        val updateBody = UpdatePermitSearchRequestBody(
            permitId = "233261",
            permitName = "Desolation Wilderness",
            groupSize = 4,
            name = "Test",
            searchType = PermitType.ZONE,
            completed = false,
            zoneTarget = PermitZoneTargetBody(divisionIds = listOf("343"), startDay = LocalDate.now(), endDay = LocalDate.now().plusDays(1)),
        )
        assertThat(doPut("/api/permit-search-requests/9999", session, updateBody).response.status).isEqualTo(404)
    }

    // --- deletePermitSearchRequest ---

    @Test
    fun `delete nonexistent permit search request returns 404`() {
        assertThat(doDelete("/api/permit-search-requests/9999", registerAndLogin()).response.status).isEqualTo(404)
    }

    @Test
    fun `delete permit search request returns 204 and subsequent GET returns 404`() {
        val session = registerAndLogin()
        seedVerifiedPhone(userRepository.findByEmail("user@test.com")!!.id!!)
        stubZonePermit("233261")
        val created = createRequest(session, zoneCreateBody)
        val id = extractId(created)

        assertThat(doDelete("/api/permit-search-requests/$id", session).response.status).isEqualTo(204)
        assertThat(
            mockMvc
                .perform(get("/api/permit-search-requests/$id").cookie(session))
                .andReturn()
                .response.status
        ).isEqualTo(404)
    }
}
