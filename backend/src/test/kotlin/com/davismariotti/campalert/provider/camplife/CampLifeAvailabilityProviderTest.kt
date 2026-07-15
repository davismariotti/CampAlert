package com.davismariotti.campalert.provider.camplife

import com.davismariotti.campalert.model.CampLifeSearchRequestDetails
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.SearchRequestState
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.provider.CallProtection
import com.davismariotti.campalert.provider.Provider
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
    private val callProtection: CallProtection =
        CallProtection
            .Builder("camplife")
            .circuitBreaker(CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()))
            .retry(RetryRegistry.of(RetryConfig.ofDefaults()))
            .build()

    private val provider =
        CampLifeAvailabilityProvider(
            campLifeApi,
            callProtection,
            campLifeCatalogCache
        )

    private val user = User(id = 1L, email = "user@example.com", passwordHash = "hash")
    private val campgroundId = 791

    private fun request(
        siteTypeId: Int? = null,
        amenityIds: List<Int>? = null,
        siteIds: List<String>? = null,
        groupSize: Int = 4,
        startDay: LocalDate = LocalDate.now().plusDays(10),
        nights: Int = 2,
        searchEndDay: LocalDate? = null,
    ): SearchRequest {
        val req = SearchRequest(
            id = 1L,
            startDay = startDay,
            nights = nights,
            groupSize = groupSize,
            campsiteId = campgroundId,
            siteIds = siteIds,
            name = "test",
            userId = 1L,
            provider = Provider.CAMPLIFE,
            searchEndDay = searchEndDay,
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
                "101" to CampLifeSite(
                    id = 101,
                    typeName = "RV Hookups",
                    maxOccupants = 6
                ),
                "102" to CampLifeSite(
                    id = 102,
                    typeName = "Tent Only",
                    maxOccupants = 4
                ),
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

    /** Mocks a distinct response per candidate window, keyed by the request's `checkinDate` string. */
    private fun mockAvailabilityForCheckins(responsesByCheckin: Map<String, Response<CampLifeAvailabilityResponse>>) {
        `when`(campLifeApi.getAvailability(org.mockito.kotlin.eq(campgroundId.toString()), org.mockito.kotlin.any())).thenAnswer { invocation ->
            val body = invocation.arguments[1] as CampLifeAvailabilityRequest

            @Suppress("UNCHECKED_CAST")
            val call = mock(Call::class.java) as Call<CampLifeAvailabilityResponse>
            `when`(call.execute()).thenReturn(responsesByCheckin.getValue(body.checkinDate))
            call
        }
    }

    @Test
    fun `sites available and not excluded by isFiltered are returned`() {
        mockAvailabilityCall(
            Response.success(
                CampLifeAvailabilityResponse(
                    sites = listOf(
                        CampLifeAvailableSite(101),
                        CampLifeAvailableSite(
                            102,
                            isFiltered = true
                        )
                    )
                )
            )
        )
        `when`(campLifeCatalogCache.getCampgroundCatalog(campgroundId)).thenReturn(catalog())

        val result = provider.checkAvailability(request(), user)

        assertTrue(result.hasAvailableSites)
        assertEquals(1, result.availableSiteCount)
        assertEquals(setOf("101"), result.availableSiteIds)
    }

    @Test
    fun `site_ids scoping is authoritative and omits siteTypeId from the request`() {
        mockAvailabilityCall(
            Response.success(
                CampLifeAvailabilityResponse(
                    sites = listOf(
                        CampLifeAvailableSite(101),
                        CampLifeAvailableSite(102)
                    )
                )
            )
        )
        `when`(campLifeCatalogCache.getCampgroundCatalog(campgroundId)).thenReturn(catalog())

        val result = provider.checkAvailability(request(siteTypeId = 1116, siteIds = listOf("102")), user)

        assertTrue(result.hasAvailableSites)
        assertEquals(setOf("102"), result.availableSiteIds)
        assertNull(capturedRequestBody().siteTypeId)
    }

    @Test
    fun `siteTypeId and amenityIds are sent through to the availability request when no site_ids are set`() {
        mockAvailabilityCall(
            Response.success(
                CampLifeAvailabilityResponse(
                    sites = listOf(
                        CampLifeAvailableSite(
                            101
                        )
                    )
                )
            )
        )
        `when`(campLifeCatalogCache.getCampgroundCatalog(campgroundId)).thenReturn(catalog())

        provider.checkAvailability(request(siteTypeId = 1116, amenityIds = listOf(234)), user)

        val body = capturedRequestBody()
        assertEquals(1116, body.siteTypeId)
        assertEquals(listOf(234), body.cgAmenity)
    }

    @Test
    fun `group size above max occupancy is excluded`() {
        mockAvailabilityCall(
            Response.success(
                CampLifeAvailabilityResponse(
                    sites = listOf(
                        CampLifeAvailableSite(
                            102
                        )
                    )
                )
            )
        )
        `when`(campLifeCatalogCache.getCampgroundCatalog(campgroundId)).thenReturn(catalog())

        val result = provider.checkAvailability(request(groupSize = 8), user)

        assertFalse(result.hasAvailableSites)
    }

    @Test
    fun `empty sites with no warnings resolves to zero availability`() {
        mockAvailabilityCall(
            Response.success(
                CampLifeAvailabilityResponse(
                    sites = emptyList()
                )
            )
        )

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
                    warnings = CampLifeMessages(
                        general = listOf(
                            CampLifeMessage("Additional sites may be available with a 2 night minimum")
                        )
                    ),
                ),
            ),
        )

        val result = provider.checkAvailability(request(), user)

        assertFalse(result.hasAvailableSites)
    }

    @Test
    fun `exact-date match sets matchedStartDay and matchedEndDay to the exact stay`() {
        val startDay = LocalDate.now().plusDays(10)
        mockAvailabilityCall(Response.success(CampLifeAvailabilityResponse(sites = listOf(CampLifeAvailableSite(101)))))
        `when`(campLifeCatalogCache.getCampgroundCatalog(campgroundId)).thenReturn(catalog())

        val result = provider.checkAvailability(request(startDay = startDay, nights = 2), user)

        assertEquals(startDay, result.matchedStartDay)
        assertEquals(startDay.plusDays(2), result.matchedEndDay)
    }

    @Test
    fun `flexible search returns the earliest matching candidate window`() {
        val startDay = LocalDate.now().plusDays(10)
        val fmt = CampLifeAvailabilityProvider.dateFormatter
        mockAvailabilityForCheckins(
            mapOf(
                startDay.format(fmt) to Response.success(CampLifeAvailabilityResponse(sites = emptyList())),
                startDay.plusDays(1).format(fmt) to Response.success(CampLifeAvailabilityResponse(sites = listOf(CampLifeAvailableSite(101)))),
                startDay.plusDays(2).format(fmt) to Response.success(CampLifeAvailabilityResponse(sites = listOf(CampLifeAvailableSite(101)))),
            ),
        )
        `when`(campLifeCatalogCache.getCampgroundCatalog(campgroundId)).thenReturn(catalog())

        val result = provider.checkAvailability(request(startDay = startDay, nights = 2, searchEndDay = startDay.plusDays(4)), user)

        assertTrue(result.hasAvailableSites)
        assertEquals(startDay.plusDays(1), result.matchedStartDay)
        assertEquals(startDay.plusDays(3), result.matchedEndDay)
    }

    @Test
    fun `flexible search with no match anywhere in the range calls once per candidate and returns unavailable`() {
        val startDay = LocalDate.now().plusDays(10)
        val fmt = CampLifeAvailabilityProvider.dateFormatter
        mockAvailabilityForCheckins(
            mapOf(
                startDay.format(fmt) to Response.success(CampLifeAvailabilityResponse(sites = emptyList())),
                startDay.plusDays(1).format(fmt) to Response.success(CampLifeAvailabilityResponse(sites = emptyList())),
                startDay.plusDays(2).format(fmt) to Response.success(CampLifeAvailabilityResponse(sites = emptyList())),
            ),
        )

        val result = provider.checkAvailability(request(startDay = startDay, nights = 2, searchEndDay = startDay.plusDays(4)), user)

        assertFalse(result.hasAvailableSites)
        assertNull(result.matchedStartDay)
        assertNull(result.matchedEndDay)
        verify(campLifeApi, org.mockito.Mockito.times(3)).getAvailability(org.mockito.kotlin.eq(campgroundId.toString()), org.mockito.kotlin.any())
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
