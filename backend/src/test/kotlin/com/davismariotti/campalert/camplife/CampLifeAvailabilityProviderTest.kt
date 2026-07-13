package com.davismariotti.campalert.camplife

import com.davismariotti.campalert.model.CampLifeSearchRequestDetails
import com.davismariotti.campalert.model.Provider
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.SearchRequestState
import com.davismariotti.campalert.model.User
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.argumentCaptor
import retrofit2.Call
import retrofit2.Response
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CampLifeAvailabilityProviderTest {
    private val campLifeApi = mock(CampLifeApi::class.java)
    private val campLifeCatalogCache = mock(CampLifeCatalogCache::class.java)
    private val callProtection = CampLifeCallProtection(
        CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()),
        RetryRegistry.of(RetryConfig.ofDefaults()),
    )

    private val provider = CampLifeAvailabilityProvider(campLifeApi, callProtection, campLifeCatalogCache)

    private val user = User(id = 1L, email = "user@example.com", passwordHash = "hash")
    private val campgroundId = 791

    private fun request(
        siteTypeId: Int? = null,
        amenityIds: List<Int>? = null,
        siteIds: List<String>? = null,
        groupSize: Int = 4
    ): SearchRequest {
        val req = SearchRequest(
            id = 1L,
            startDay = LocalDate.now().plusDays(10),
            nights = 2,
            groupSize = groupSize,
            campsiteId = campgroundId,
            siteIds = siteIds,
            name = "test",
            userId = 1L,
            provider = Provider.CAMPLIFE,
        )
        val state = SearchRequestState()
        state.searchRequest = req
        req.state = state
        if (siteTypeId != null || amenityIds != null) {
            val details = CampLifeSearchRequestDetails()
            details.searchRequest = req
            details.siteTypeId = siteTypeId
            details.amenityIds = amenityIds
            req.campLifeDetails = details
        }
        return req
    }

    private fun catalog() =
        CampLifeSessionResponse(
            siteMap = mapOf(
                "101" to CampLifeSite(id = 101, typeName = "RV Hookups", maxOccupants = 6),
                "102" to CampLifeSite(id = 102, typeName = "Tent Only", maxOccupants = 4),
            ),
        )

    private fun mockAvailabilityCall(response: Response<CampLifeAvailabilityResponse>) {
        @Suppress("UNCHECKED_CAST")
        val call = mock(Call::class.java) as Call<CampLifeAvailabilityResponse>
        `when`(call.execute()).thenReturn(response)
        `when`(campLifeApi.getAvailability(org.mockito.kotlin.eq(campgroundId.toString()), org.mockito.kotlin.any())).thenReturn(call)
    }

    private fun capturedRequestBody(): CampLifeAvailabilityRequest {
        val captor = argumentCaptor<CampLifeAvailabilityRequest>()
        verify(campLifeApi).getAvailability(org.mockito.kotlin.eq(campgroundId.toString()), captor.capture())
        return captor.firstValue
    }

    @Test
    fun `sites available and not excluded by isFiltered are returned`() {
        mockAvailabilityCall(Response.success(CampLifeAvailabilityResponse(sites = listOf(CampLifeAvailableSite(101), CampLifeAvailableSite(102, isFiltered = true)))))
        `when`(campLifeCatalogCache.getCampgroundCatalog(campgroundId)).thenReturn(catalog())

        val result = provider.checkAvailability(request(), user)

        assertTrue(result.hasAvailableSites)
        assertEquals(1, result.availableSiteCount)
        assertEquals(setOf("101"), result.availableSiteIds)
    }

    @Test
    fun `site_ids scoping is authoritative and omits siteTypeId from the request`() {
        mockAvailabilityCall(Response.success(CampLifeAvailabilityResponse(sites = listOf(CampLifeAvailableSite(101), CampLifeAvailableSite(102)))))
        `when`(campLifeCatalogCache.getCampgroundCatalog(campgroundId)).thenReturn(catalog())

        val result = provider.checkAvailability(request(siteTypeId = 1116, siteIds = listOf("102")), user)

        assertTrue(result.hasAvailableSites)
        assertEquals(setOf("102"), result.availableSiteIds)
        assertNull(capturedRequestBody().siteTypeId)
    }

    @Test
    fun `siteTypeId and amenityIds are sent through to the availability request when no site_ids are set`() {
        mockAvailabilityCall(Response.success(CampLifeAvailabilityResponse(sites = listOf(CampLifeAvailableSite(101)))))
        `when`(campLifeCatalogCache.getCampgroundCatalog(campgroundId)).thenReturn(catalog())

        provider.checkAvailability(request(siteTypeId = 1116, amenityIds = listOf(234)), user)

        val body = capturedRequestBody()
        assertEquals(1116, body.siteTypeId)
        assertEquals(listOf(234), body.cgAmenity)
    }

    @Test
    fun `group size above max occupancy is excluded`() {
        mockAvailabilityCall(Response.success(CampLifeAvailabilityResponse(sites = listOf(CampLifeAvailableSite(102)))))
        `when`(campLifeCatalogCache.getCampgroundCatalog(campgroundId)).thenReturn(catalog())

        val result = provider.checkAvailability(request(groupSize = 8), user)

        assertFalse(result.hasAvailableSites)
    }

    @Test
    fun `empty sites with no warnings resolves to zero availability`() {
        mockAvailabilityCall(Response.success(CampLifeAvailabilityResponse(sites = emptyList())))

        val result = provider.checkAvailability(request(), user)

        assertFalse(result.hasAvailableSites)
        assertEquals(0, result.availableSiteCount)
        assertEquals(emptySet(), result.availableSiteIds)
    }

    @Test
    fun `HTTP 400 same-day error resolves to zero availability without throwing`() {
        val errorJson = """{"sites":[],"errors":{"general":[{"message":"Same Day reservations are not allowed online."}]}}"""
        val errorResponse = Response.error<CampLifeAvailabilityResponse>(
            400,
            errorJson.toResponseBody("application/json".toMediaType()),
        )
        mockAvailabilityCall(errorResponse)

        val result = provider.checkAvailability(request(), user)

        assertFalse(result.hasAvailableSites)
        assertEquals(0, result.availableSiteCount)
    }

    @Test
    fun `HTTP 200 with minimum-stay warning resolves to zero availability`() {
        mockAvailabilityCall(
            Response.success(
                CampLifeAvailabilityResponse(
                    sites = emptyList(),
                    warnings = CampLifeMessages(general = listOf(CampLifeMessage("Additional sites may be available with a 2 night minimum"))),
                ),
            ),
        )

        val result = provider.checkAvailability(request(), user)

        assertFalse(result.hasAvailableSites)
    }

    @Test
    fun `exception during availability call resolves to zero availability`() {
        @Suppress("UNCHECKED_CAST")
        val call = mock(Call::class.java) as Call<CampLifeAvailabilityResponse>
        `when`(call.execute()).thenThrow(RuntimeException("network error"))
        `when`(campLifeApi.getAvailability(org.mockito.kotlin.eq(campgroundId.toString()), org.mockito.kotlin.any())).thenReturn(call)

        val result = provider.checkAvailability(request(), user)

        assertFalse(result.hasAvailableSites)
    }
}
