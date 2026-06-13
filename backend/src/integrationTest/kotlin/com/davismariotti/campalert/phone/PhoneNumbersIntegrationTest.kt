package com.davismariotti.campalert.phone

import com.davismariotti.campalert.api.model.AddPhoneNumberBody
import com.davismariotti.campalert.api.model.VerifyPhoneNumberBody
import com.davismariotti.campalert.model.PhoneNumber
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.sms.VerifyResult
import com.davismariotti.campalert.support.IntegrationTestBase
import com.twilio.exception.ApiConnectionException
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import java.time.Instant
import java.time.LocalDate

class PhoneNumbersIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var phoneNumberRepository: PhoneNumberRepository

    @Autowired
    private lateinit var searchRequestRepository: SearchRequestRepository

    @BeforeEach
    fun stubTwilio() {
        Mockito.doNothing().`when`(twilioVerifyService).startVerification(anyString())
        Mockito.`when`(twilioVerifyService.checkVerification(anyString(), anyString()))
            .thenReturn(VerifyResult.Approved)
    }

    // --- helpers ---

    private fun addPhone(session: Cookie, phone: String = "+12125551234", consent: Boolean = true): MvcResult =
        doPost("/api/phone-numbers", session, AddPhoneNumberBody(phone = phone, smsConsent = consent))

    private fun verifyPhone(session: Cookie, id: Long, code: String = "123456"): MvcResult =
        doPost("/api/phone-numbers/$id/verify", session, VerifyPhoneNumberBody(code = code))

    private fun deletePhone(session: Cookie, id: Long): MvcResult = doDelete("/api/phone-numbers/$id", session)

    private fun seedVerifiedPhone(userId: Long, phone: String = "+12125551234"): PhoneNumber =
        phoneNumberRepository.save(
            PhoneNumber(
                userId = userId,
                phone = phone,
                status = PhoneNumberStatus.VERIFIED,
                smsConsentAt = Instant.now(),
            )
        )

    private fun seedSearchRequest(userId: Long, pauseReason: String? = null): SearchRequest =
        searchRequestRepository.save(
            SearchRequest(
                userId = userId,
                startDay = LocalDate.now().plusDays(30),
                nights = 1,
                groupSize = 2,
                campsiteId = 99,
                name = "Test Request",
                campgroundName = "Test Campground",
                completed = false,
                pauseReason = pauseReason,
            )
        )

    // --- 2.3 Unauthenticated 401 ---

    @Test
    fun `unauthenticated GET phone numbers returns 401`() {
        assertThat(mockMvc.perform(get("/api/phone-numbers")).andReturn().response.status).isEqualTo(401)
    }

    @Test
    fun `unauthenticated POST add phone number returns 401`() {
        val result = doPost("/api/phone-numbers", body = AddPhoneNumberBody(phone = "+12125551234", smsConsent = true))
        assertThat(result.response.status).isEqualTo(401)
    }

    @Test
    fun `unauthenticated POST verify phone number returns 401`() {
        val result = doPost("/api/phone-numbers/1/verify", body = VerifyPhoneNumberBody(code = "123456"))
        assertThat(result.response.status).isEqualTo(401)
    }

    @Test
    fun `unauthenticated DELETE phone number returns 401`() {
        assertThat(doDelete("/api/phone-numbers/1").response.status).isEqualTo(401)
    }

    // --- 2.4 addPhoneNumber validation ---

    @Test
    fun `smsConsent false returns 400`() {
        val session = registerAndLogin()
        assertThat(addPhone(session, consent = false).response.status).isEqualTo(400)
    }

    @Test
    fun `phone not in E164 format returns 400`() {
        val session = registerAndLogin()
        assertThat(addPhone(session, phone = "5551234567").response.status).isEqualTo(400)
    }

    @Test
    fun `duplicate phone returns 409 with PHONE_ALREADY_REGISTERED`() {
        val session = registerAndLogin()
        addPhone(session)
        val second = addPhone(session)
        assertThat(second.response.status).isEqualTo(409)
        assertThat(second.response.contentAsString).contains("PHONE_ALREADY_REGISTERED")
    }

    // --- 2.5 addPhoneNumber success ---

    @Test
    fun `valid request creates PENDING_VERIFICATION phone and returns 201`() {
        val session = registerAndLogin()
        val result = addPhone(session)
        assertThat(result.response.status).isEqualTo(201)
        assertThat(result.response.contentAsString).contains("PENDING_VERIFICATION")
    }

    // --- 2.6 Twilio failure ---

    @Test
    fun `Twilio exception on startVerification returns 502`() {
        val session = registerAndLogin()
        Mockito.doThrow(
            ApiConnectionException("twilio down")
        ).`when`(twilioVerifyService).startVerification(anyString())
        assertThat(addPhone(session).response.status).isEqualTo(502)
    }

    // --- 2.7 listPhoneNumbers ---

    @Test
    fun `list returns empty array when user has no phone numbers`() {
        val session = registerAndLogin()
        val result = mockMvc.perform(get("/api/phone-numbers").cookie(session)).andReturn()
        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo("[]")
    }

    @Test
    fun `list returns phone numbers belonging to authenticated user`() {
        val session = registerAndLogin()
        addPhone(session)
        val result = mockMvc.perform(get("/api/phone-numbers").cookie(session)).andReturn()
        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("+12125551234")
    }

    @Test
    fun `list does not return other users phone numbers`() {
        val session1 = registerAndLogin("user1@test.com")
        addPhone(session1, "+12125551111")
        val session2 = registerAndLogin("user2@test.com")
        val result = mockMvc.perform(get("/api/phone-numbers").cookie(session2)).andReturn()
        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo("[]")
    }

    // --- 2.8 verifyPhoneNumber errors ---

    @Test
    fun `verify nonexistent phone returns 404`() {
        assertThat(verifyPhone(registerAndLogin(), 9999L).response.status).isEqualTo(404)
    }

    @Test
    fun `verify phone belonging to another user returns 404`() {
        val session1 = registerAndLogin("user1@test.com")
        val id = extractId(addPhone(session1))
        val session2 = registerAndLogin("user2@test.com")
        assertThat(verifyPhone(session2, id).response.status).isEqualTo(404)
    }

    @Test
    fun `verify phone not in PENDING_VERIFICATION state returns 422 with NOT_PENDING_VERIFICATION`() {
        val session = registerAndLogin()
        val user = userRepository.findByEmail("user@test.com")!!
        val phone = seedVerifiedPhone(user.id!!)
        val result = verifyPhone(session, phone.id!!)
        assertThat(result.response.status).isEqualTo(422)
        assertThat(result.response.contentAsString).contains("NOT_PENDING_VERIFICATION")
    }

    @Test
    fun `wrong OTP code returns 422 with INVALID_OTP`() {
        val session = registerAndLogin()
        val id = extractId(addPhone(session))
        Mockito.`when`(twilioVerifyService.checkVerification(anyString(), anyString()))
            .thenReturn(VerifyResult.InvalidCode)
        val result = verifyPhone(session, id, "000000")
        assertThat(result.response.status).isEqualTo(422)
        assertThat(result.response.contentAsString).contains("INVALID_OTP")
    }

    @Test
    fun `expired OTP returns 422 with OTP_EXPIRED`() {
        val session = registerAndLogin()
        val id = extractId(addPhone(session))
        Mockito.`when`(twilioVerifyService.checkVerification(anyString(), anyString()))
            .thenReturn(VerifyResult.Expired)
        val result = verifyPhone(session, id, "000000")
        assertThat(result.response.status).isEqualTo(422)
        assertThat(result.response.contentAsString).contains("OTP_EXPIRED")
    }

    // --- 2.9 verifyPhoneNumber success ---

    @Test
    fun `correct OTP transitions phone to VERIFIED and returns 200`() {
        val session = registerAndLogin()
        val id = extractId(addPhone(session))
        val result = verifyPhone(session, id)
        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("VERIFIED")
    }

    // --- 2.10 supersede ---

    @Test
    fun `verifying phone B deletes previously VERIFIED phone A`() {
        val session = registerAndLogin()
        val user = userRepository.findByEmail("user@test.com")!!
        seedVerifiedPhone(user.id!!, "+12125551111")

        val idB = extractId(addPhone(session, "+12125552222"))
        verifyPhone(session, idB)

        val phones = phoneNumberRepository.findByUserId(user.id!!)
        assertThat(phones).hasSize(1)
        assertThat(phones[0].phone).isEqualTo("+12125552222")
    }

    // --- 2.11 resume paused search requests ---

    @Test
    fun `verifying phone clears NO_VERIFIED_PHONE pause reason on search requests`() {
        val session = registerAndLogin()
        val user = userRepository.findByEmail("user@test.com")!!
        val request = seedSearchRequest(user.id!!, pauseReason = "NO_VERIFIED_PHONE")

        val id = extractId(addPhone(session))
        verifyPhone(session, id)

        val updated = searchRequestRepository.findById(request.id!!).orElseThrow()
        assertThat(updated.pauseReason).isNull()
    }

    // --- 2.12 deletePhoneNumber errors ---

    @Test
    fun `delete nonexistent phone returns 404`() {
        assertThat(deletePhone(registerAndLogin(), 9999L).response.status).isEqualTo(404)
    }

    @Test
    fun `delete phone belonging to another user returns 404`() {
        val session1 = registerAndLogin("user1@test.com")
        val id = extractId(addPhone(session1))
        val session2 = registerAndLogin("user2@test.com")
        assertThat(deletePhone(session2, id).response.status).isEqualTo(404)
    }

    // --- 2.13 deletePhoneNumber success ---

    @Test
    fun `delete phone returns 204`() {
        val session = registerAndLogin()
        val id = extractId(addPhone(session))
        assertThat(deletePhone(session, id).response.status).isEqualTo(204)
    }

    // --- 2.14 pause on delete ---

    @Test
    fun `deleting only verified phone pauses active search requests`() {
        val session = registerAndLogin()
        val user = userRepository.findByEmail("user@test.com")!!
        val phone = seedVerifiedPhone(user.id!!)
        val request = seedSearchRequest(user.id!!)

        deletePhone(session, phone.id!!)

        val updated = searchRequestRepository.findById(request.id!!).orElseThrow()
        assertThat(updated.pauseReason).isEqualTo("NO_VERIFIED_PHONE")
    }

    // --- 2.15 no pause on pending delete (when verified phone still exists) ---

    @Test
    fun `deleting PENDING_VERIFICATION phone does not pause search requests when verified phone still exists`() {
        val session = registerAndLogin()
        val user = userRepository.findByEmail("user@test.com")!!
        seedVerifiedPhone(user.id!!, "+12125551111")
        val request = seedSearchRequest(user.id!!)

        // Add and delete a pending phone — the verified phone above remains untouched
        val pendingId = extractId(addPhone(session, "+12125552222"))
        deletePhone(session, pendingId)

        val updated = searchRequestRepository.findById(request.id!!).orElseThrow()
        assertThat(updated.pauseReason).isNull()
    }
}
