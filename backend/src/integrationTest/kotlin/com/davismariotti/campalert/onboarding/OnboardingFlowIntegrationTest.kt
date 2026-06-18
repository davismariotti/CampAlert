package com.davismariotti.campalert.onboarding

import com.davismariotti.campalert.api.model.AddPhoneNumberBody
import com.davismariotti.campalert.api.model.CreateSearchRequestBody
import com.davismariotti.campalert.api.model.VerifyPhoneNumberBody
import com.davismariotti.campalert.recreation.RidbFacility
import com.davismariotti.campalert.recreation.RidbFacilityResponse
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.sms.VerifyResult
import com.davismariotti.campalert.support.IntegrationTestBase
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import retrofit2.Call
import retrofit2.Response
import java.time.LocalDate

class OnboardingFlowIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var phoneNumberRepository: PhoneNumberRepository

    @Autowired
    private lateinit var searchRequestRepository: SearchRequestRepository

    @BeforeEach
    fun stubExternalDependencies() {
        Mockito.doNothing().`when`(twilioVerifyService).startVerification(anyString())
        Mockito
            .`when`(twilioVerifyService.checkVerification(anyString(), anyString()))
            .thenReturn(VerifyResult.Approved)

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
        val facilityCall = successCall(noCoordFacility)
        Mockito.`when`(ridbApi.getFacility(anyInt())).thenReturn(facilityCall)
    }

    // --- helpers ---

    @Suppress("UNCHECKED_CAST")
    private fun <T> successCall(body: T): Call<T> {
        val call = Mockito.mock(Call::class.java) as Call<T>
        Mockito.doReturn(Response.success(body)).`when`(call).execute()
        return call
    }

    private fun addPhone(session: Cookie, phone: String = "+12125551234"): MvcResult = doPost("/api/phone-numbers", session, AddPhoneNumberBody(phone = phone, smsConsent = true))

    private fun verifyPhone(session: Cookie, id: Long): MvcResult = doPost("/api/phone-numbers/$id/verify", session, VerifyPhoneNumberBody(code = "123456"))

    private fun deletePhone(session: Cookie, id: Long): MvcResult = doDelete("/api/phone-numbers/$id", session)

    private fun createSearchRequest(session: Cookie): MvcResult =
        doPost(
            "/api/search-requests",
            session,
            CreateSearchRequestBody(
                startDay = LocalDate.of(2027, 7, 1),
                nights = 2,
                groupSize = 4,
                campsiteId = 10,
                campgroundName = "Pine Valley",
                name = "Summer Trip",
            )
        )

    // --- 5.2 happy path ---

    @Test
    fun `register login add phone verify create search request succeeds end to end`() {
        val session = registerAndLogin()

        val phoneResult = addPhone(session)
        assertThat(phoneResult.response.status).isEqualTo(201)
        val phoneId = extractId(phoneResult)

        val verifyResult = verifyPhone(session, phoneId)
        assertThat(verifyResult.response.status).isEqualTo(200)
        assertThat(verifyResult.response.contentAsString).contains("VERIFIED")

        val searchResult = createSearchRequest(session)
        assertThat(searchResult.response.status).isEqualTo(201)
        val searchId = extractId(searchResult)

        val getResult = mockMvc.perform(get("/api/search-requests/$searchId").cookie(session)).andReturn()
        assertThat(getResult.response.status).isEqualTo(200)
        assertThat(getResult.response.contentAsString).contains("Summer Trip")
    }

    // --- 5.3 blocked before verify ---

    @Test
    fun `cannot create search request when phone is pending verification`() {
        val session = registerAndLogin()
        addPhone(session) // PENDING_VERIFICATION, not verified
        val result = createSearchRequest(session)
        assertThat(result.response.status).isEqualTo(422)
        assertThat(result.response.contentAsString).contains("NO_VERIFIED_PHONE")
    }

    // --- 5.4 supersede keeps search requests active ---

    @Test
    fun `verifying a second phone keeps search requests active`() {
        val session = registerAndLogin()
        val user = userRepository.findByEmail("user@test.com")!!

        val phoneAId = extractId(addPhone(session, "+12125551111"))
        verifyPhone(session, phoneAId)

        val searchResult = createSearchRequest(session)
        assertThat(searchResult.response.status).isEqualTo(201)
        val searchId = extractId(searchResult)

        val phoneBId = extractId(addPhone(session, "+12125552222"))
        verifyPhone(session, phoneBId)

        val request = searchRequestRepository.findById(searchId).orElseThrow()
        assertThat(request.pauseReason).isNull()

        val phones = phoneNumberRepository.findByUserId(user.id!!)
        assertThat(phones).hasSize(1)
        assertThat(phones[0].phone).isEqualTo("+12125552222")
    }

    // --- 5.5 delete and resume cycle ---

    @Test
    fun `deleting verified phone pauses search requests and re-verifying resumes them`() {
        val session = registerAndLogin()

        val phoneId = extractId(addPhone(session))
        verifyPhone(session, phoneId)

        val searchId = extractId(createSearchRequest(session))

        deletePhone(session, phoneId)
        assertThat(searchRequestRepository.findById(searchId).orElseThrow().pauseReason).isEqualTo("NO_VERIFIED_PHONE")

        val newPhoneId = extractId(addPhone(session, "+12125559999"))
        verifyPhone(session, newPhoneId)
        assertThat(searchRequestRepository.findById(searchId).orElseThrow().pauseReason).isNull()
    }
}
