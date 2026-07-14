package com.davismariotti.campalert.campground

import com.davismariotti.campalert.provider.recreation.AvailabilityType
import com.davismariotti.campalert.provider.recreation.Campground
import com.davismariotti.campalert.provider.recreation.Campsite
import com.davismariotti.campalert.provider.recreation.RidbCampsite
import com.davismariotti.campalert.provider.recreation.RidbCampsitesResponse
import com.davismariotti.campalert.provider.recreation.RidbFacilitiesResponse
import com.davismariotti.campalert.provider.recreation.RidbFacility
import com.davismariotti.campalert.support.IntegrationTestBase
import jakarta.servlet.http.Cookie
import okhttp3.ResponseBody.Companion.toResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import retrofit2.Call
import retrofit2.Response
import java.time.ZoneOffset
import java.time.ZonedDateTime

class CampgroundsIntegrationTest : IntegrationTestBase() {
    // --- helpers ---

    @Suppress("UNCHECKED_CAST")
    private fun <T> successCall(body: T): Call<T> {
        val call = Mockito.mock(Call::class.java) as Call<T>
        Mockito.doReturn(Response.success(body)).`when`(call).execute()
        return call
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> errorCall(code: Int): Call<T> {
        val call = Mockito.mock(Call::class.java) as Call<T>
        Mockito
            .doReturn(
                Response.error<T>(code, "error".toResponseBody(null))
            ).`when`(call)
            .execute()
        return call
    }

    private fun loginSession(): Cookie = registerAndLogin()

    private fun campsite(id: Int, site: String, loop: String): Campsite {
        val dt = ZonedDateTime.now(ZoneOffset.UTC)
        return Campsite(
            campsiteId = id,
            site = site,
            loop = loop,
            campsiteReserveType = "SITE_SPECIFIC",
            availabilities = mapOf(dt to AvailabilityType.AVAILABLE),
            quantities = mapOf(dt to 1),
            minimumNumberOfPeople = 1,
            maximumNumberOfPeople = 6,
        )
    }

    // --- 4.2 Unauthenticated 401 ---

    @Test
    fun `unauthenticated search campgrounds returns 401`() {
        val result = mockMvc.perform(get("/api/campground-search?q=yosemite")).andReturn()
        assertThat(result.response.status).isEqualTo(401)
    }

    @Test
    fun `unauthenticated get campground returns 401`() {
        val result = mockMvc.perform(get("/api/campgrounds/123")).andReturn()
        assertThat(result.response.status).isEqualTo(401)
    }

    @Test
    fun `unauthenticated get campground loops returns 401`() {
        val result = mockMvc.perform(get("/api/campgrounds/123/loops")).andReturn()
        assertThat(result.response.status).isEqualTo(401)
    }

    // --- 4.3 searchCampgrounds blank q ---

    @Test
    fun `blank q parameter returns 400`() {
        val session = loginSession()
        val result = mockMvc.perform(get("/api/campground-search?q=").cookie(session)).andReturn()
        assertThat(result.response.status).isEqualTo(400)
    }

    // --- 4.4 searchCampgrounds circuit open ---

    @Test
    fun `searchCampgrounds with RIDB circuit open returns 200 with empty list`() {
        val session = loginSession()
        circuitBreakerRegistry.circuitBreaker("ridb").transitionToOpenState()
        val result = mockMvc.perform(get("/api/campground-search?q=yosemite").cookie(session)).andReturn()
        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo("[]")
    }

    // --- 4.5 searchCampgrounds RIDB error ---

    @Test
    fun `unscoped searchCampgrounds with RIDB error degrades to 200 with empty list`() {
        // Unscoped search merges every registered provider; one provider's failure doesn't fail the
        // whole request (design.md decision 7) — RIDB contributes nothing, CampLife (mocked, unstubbed)
        // contributes nothing either, so the merged result is an empty list, not an error.
        val session = loginSession()
        val call = errorCall<RidbFacilitiesResponse>(500)
        Mockito.`when`(ridbApi.getFacilities(anyString(), anyInt())).thenReturn(call)
        val result = mockMvc.perform(get("/api/campground-search?q=yosemite").cookie(session)).andReturn()
        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo("[]")
    }

    @Test
    fun `searchCampgrounds scoped to RECREATION_GOV with RIDB error response returns 502`() {
        val session = loginSession()
        val call = errorCall<RidbFacilitiesResponse>(500)
        Mockito.`when`(ridbApi.getFacilities(anyString(), anyInt())).thenReturn(call)
        val result = mockMvc.perform(get("/api/campground-search?q=yosemite&provider=RECREATION_GOV").cookie(session)).andReturn()
        assertThat(result.response.status).isEqualTo(502)
    }

    // --- 4.6 searchCampgrounds filtering ---

    @Test
    fun `searchCampgrounds filters out non-Campground facility types`() {
        val session = loginSession()
        val facilities = listOf(
            RidbFacility("1", "Yosemite Valley", "Campground", null, 37.7, -119.5),
            RidbFacility("2", "Yosemite Day Use", "Day Use Area", null, 37.7, -119.5),
        )
        val call = successCall(RidbFacilitiesResponse(facilities))
        Mockito.`when`(ridbApi.getFacilities(anyString(), anyInt())).thenReturn(call)

        val result = mockMvc.perform(get("/api/campground-search?q=yosemite").cookie(session)).andReturn()
        assertThat(result.response.status).isEqualTo(200)
        val tree = mapper.readTree(result.response.contentAsString)
        assertThat(tree.size()).isEqualTo(1)
        assertThat(tree[0].get("name").asText()).isEqualTo("Yosemite Valley")
    }

    // --- 4.7 searchCampgrounds mapping ---

    @Test
    fun `searchCampgrounds maps facilityId and facilityName correctly`() {
        val session = loginSession()
        val facilities = listOf(
            RidbFacility("12345", "Pine Valley CG", "Campground", null, null, null)
        )
        val call = successCall(RidbFacilitiesResponse(facilities))
        Mockito.`when`(ridbApi.getFacilities(anyString(), anyInt())).thenReturn(call)

        val result = mockMvc.perform(get("/api/campground-search?q=pine").cookie(session)).andReturn()
        val tree = mapper.readTree(result.response.contentAsString)
        assertThat(tree[0].get("id").asInt()).isEqualTo(12345)
        assertThat(tree[0].get("name").asText()).isEqualTo("Pine Valley CG")
    }

    // --- 4.8 getCampground null body ---

    @Test
    fun `getCampground returns 404 when Recreation gov returns null body`() {
        val session = loginSession()
        val call = successCall<Campground?>(null)
        @Suppress("UNCHECKED_CAST")
        Mockito
            .`when`(recreationApi.getCampgroundAvailability(anyInt(), anyString()))
            .thenReturn(call as Call<Campground>)
        val result = mockMvc.perform(get("/api/campgrounds/123").cookie(session)).andReturn()
        assertThat(result.response.status).isEqualTo(404)
    }

    // --- 4.9 getCampground success ---

    @Test
    fun `getCampground returns campsite map with availabilities`() {
        val session = loginSession()
        val site = campsite(id = 101, site = "A01", loop = "A Loop")
        val call = successCall(Campground(campsites = mapOf(101 to site)))
        Mockito.`when`(recreationApi.getCampgroundAvailability(anyInt(), anyString())).thenReturn(call)

        val result = mockMvc.perform(get("/api/campgrounds/123").cookie(session)).andReturn()
        assertThat(result.response.status).isEqualTo(200)
        val tree = mapper.readTree(result.response.contentAsString)
        val cs = tree.get("campsites").get("101")
        assertThat(cs.get("site").asText()).isEqualTo("A01")
        assertThat(cs.get("loop").asText()).isEqualTo("A Loop")
    }

    // --- 4.10 getCampgroundLoops circuit open ---

    @Test
    fun `getCampgroundLoops with RIDB circuit open returns 200 with empty list`() {
        val session = loginSession()
        circuitBreakerRegistry.circuitBreaker("ridb").transitionToOpenState()
        val result = mockMvc.perform(get("/api/campgrounds/123/loops").cookie(session)).andReturn()
        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo("[]")
    }

    // --- 4.11 getCampgroundLoops RIDB error ---

    @Test
    fun `getCampgroundLoops with RIDB error response returns 502`() {
        val session = loginSession()
        val call = errorCall<RidbCampsitesResponse>(500)
        Mockito.`when`(ridbApi.getCampsites(anyInt())).thenReturn(call)
        val result = mockMvc.perform(get("/api/campgrounds/123/loops").cookie(session)).andReturn()
        assertThat(result.response.status).isEqualTo(502)
    }

    // --- 4.12 blank loop filtering ---

    @Test
    fun `getCampgroundLoops excludes campsites with null or blank loop`() {
        val session = loginSession()
        val campsites = listOf(
            RidbCampsite(loop = "Alpine", campsiteType = "STANDARD"),
            RidbCampsite(loop = null, campsiteType = "STANDARD"),
            RidbCampsite(loop = "  ", campsiteType = "STANDARD"),
        )
        val call = successCall(RidbCampsitesResponse(campsites))
        Mockito.`when`(ridbApi.getCampsites(anyInt())).thenReturn(call)

        val result = mockMvc.perform(get("/api/campgrounds/123/loops").cookie(session)).andReturn()
        val tree = mapper.readTree(result.response.contentAsString)
        assertThat(tree.size()).isEqualTo(1)
        assertThat(tree[0].get("name").asText()).isEqualTo("Alpine")
    }

    // --- 4.13 sort ---

    @Test
    fun `getCampgroundLoops returns loops sorted alphabetically`() {
        val session = loginSession()
        val campsites = listOf(
            RidbCampsite(loop = "Zephyr", campsiteType = "STANDARD"),
            RidbCampsite(loop = "Alpine", campsiteType = "STANDARD"),
            RidbCampsite(loop = "Meadow", campsiteType = "STANDARD"),
        )
        val call = successCall(RidbCampsitesResponse(campsites))
        Mockito.`when`(ridbApi.getCampsites(anyInt())).thenReturn(call)

        val result = mockMvc.perform(get("/api/campgrounds/123/loops").cookie(session)).andReturn()
        val tree = mapper.readTree(result.response.contentAsString)
        assertThat(
            tree
                .iterator()
                .asSequence()
                .map { it.get("name").asText() }
                .toList()
        ).containsExactly("Alpine", "Meadow", "Zephyr")
    }

    // --- 4.14 boat-in detection ---

    @Test
    fun `loop with all BOAT IN sites is marked boatInOnly=true`() {
        val session = loginSession()
        val campsites = listOf(
            RidbCampsite(loop = "Lakeshore", campsiteType = "BOAT IN"),
            RidbCampsite(loop = "Lakeshore", campsiteType = "BOAT IN"),
        )
        val call = successCall(RidbCampsitesResponse(campsites))
        Mockito.`when`(ridbApi.getCampsites(anyInt())).thenReturn(call)

        val result = mockMvc.perform(get("/api/campgrounds/123/loops").cookie(session)).andReturn()
        val tree = mapper.readTree(result.response.contentAsString)
        assertThat(tree[0].get("boatInOnly").asBoolean()).isTrue()
    }

    @Test
    fun `loop name containing BOAT is marked boatInOnly=true regardless of site type`() {
        val session = loginSession()
        val campsites = listOf(
            RidbCampsite(loop = "BOAT ACCESS LOOP", campsiteType = "STANDARD"),
        )
        val call = successCall(RidbCampsitesResponse(campsites))
        Mockito.`when`(ridbApi.getCampsites(anyInt())).thenReturn(call)

        val result = mockMvc.perform(get("/api/campgrounds/123/loops").cookie(session)).andReturn()
        val tree = mapper.readTree(result.response.contentAsString)
        assertThat(tree[0].get("boatInOnly").asBoolean()).isTrue()
    }

    @Test
    fun `loop with mixed site types and non-BOAT name is not boatInOnly`() {
        val session = loginSession()
        val campsites = listOf(
            RidbCampsite(loop = "Ridge", campsiteType = "BOAT IN"),
            RidbCampsite(loop = "Ridge", campsiteType = "STANDARD"),
        )
        val call = successCall(RidbCampsitesResponse(campsites))
        Mockito.`when`(ridbApi.getCampsites(anyInt())).thenReturn(call)

        val result = mockMvc.perform(get("/api/campgrounds/123/loops").cookie(session)).andReturn()
        val tree = mapper.readTree(result.response.contentAsString)
        assertThat(tree[0].get("boatInOnly").asBoolean()).isFalse()
    }
}
