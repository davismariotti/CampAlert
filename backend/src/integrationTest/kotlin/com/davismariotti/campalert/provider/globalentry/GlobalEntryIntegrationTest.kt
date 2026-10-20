package com.davismariotti.campalert.provider.globalentry

import com.davismariotti.campalert.api.model.CreateSearchRequestBody
import com.davismariotti.campalert.api.model.Provider
import com.davismariotti.campalert.api.model.ProviderType
import com.davismariotti.campalert.api.model.UpdateSearchRequestBody
import com.davismariotti.campalert.model.PhoneNumber
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.scheduling.CampgroundPollCheckService
import com.davismariotti.campalert.support.IntegrationTestBase
import jakarta.servlet.http.Cookie
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import retrofit2.Call
import retrofit2.Response
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class GlobalEntryIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var phoneNumberRepository: PhoneNumberRepository

    @Autowired
    private lateinit var catalogCache: GlobalEntryCatalogCache

    @Autowired
    private lateinit var pollCheckService: CampgroundPollCheckService

    private val centerId = 5446
    private val geProvider = Provider(type = ProviderType.GLOBAL_ENTRY, name = "Global Entry")

    // Center-local "today" is what the poll check uses, so ranges are built from it.
    private val today: LocalDate = LocalDate.now(ZoneId.of("America/Los_Angeles"))
    private val day: LocalDate = today.plusDays(7)

    private val directory = listOf(
        GlobalEntryLocation(centerId, "SF Enrollment Center", "SF Enrollment Center", "San Francisco", "CA", "America/Los_Angeles"),
        GlobalEntryLocation(5140, "San Jose Enrollment Center", "San Jose", "San Jose", "CA", "America/Los_Angeles"),
        GlobalEntryLocation(9999, "Virtual Interview", "Virtual", "Nowhere", "ZZ", "UTC", remoteInd = true),
    )

    @BeforeEach
    fun stubScheduler() {
        @Suppress("UNCHECKED_CAST")
        val locationsCall = Mockito.mock(Call::class.java) as Call<List<GlobalEntryLocation>>
        Mockito.doAnswer { Response.success(directory) }.`when`(locationsCall).execute()
        Mockito.`when`(globalEntryApi.getLocations(any(), any(), any(), any())).thenReturn(locationsCall)
    }

    private fun stubSlots(vararg slotTimes: String) {
        val json = slotTimes.joinToString(prefix = "[", postfix = "]") { """{"timestamp":"$it","active":1,"total":3}""" }

        @Suppress("UNCHECKED_CAST")
        val slotsCall = Mockito.mock(Call::class.java) as Call<ResponseBody>
        Mockito.doAnswer { Response.success(json.toResponseBody("application/json".toMediaType())) }.`when`(slotsCall).execute()
        Mockito.`when`(globalEntryApi.getSlots(anyInt(), anyString(), anyString())).thenReturn(slotsCall)
    }

    private fun enableGlobalEntry() {
        jdbcTemplate.update("UPDATE global_provider_access SET enabled = true WHERE provider = 'GLOBAL_ENTRY'")
    }

    private fun userWithPhone(): Cookie {
        val session = registerAndLogin()
        phoneNumberRepository.save(
            PhoneNumber(
                userId = userRepository.findByEmail("user@test.com")!!.id!!,
                phone = "+12125550100",
                status = PhoneNumberStatus.VERIFIED,
                smsConsentAt = Instant.now(),
            ),
        )
        return session
    }

    private fun createBody(earliestTime: String? = "11:30", latestTime: String? = "14:00", latestStartDay: LocalDate? = day.plusDays(2)) =
        CreateSearchRequestBody(
            startDay = day,
            nights = 3,
            groupSize = 4,
            campsiteId = centerId,
            campgroundName = "client supplied",
            name = "Global Entry",
            provider = geProvider,
            earliestTime = earliestTime,
            latestTime = latestTime,
            latestStartDay = latestStartDay,
            turnstileToken = "test-token",
        )

    private fun getJson(path: String, session: Cookie) =
        mapper.readTree(
            mockMvc
                .perform(get(path).cookie(session))
                .andReturn()
                .response.contentAsString
        )

    // --- catalog cache ---

    @Test
    fun `directory survives a real Redis JSON round trip and excludes remote locations`() {
        val first = catalogCache.getDirectory()
        val second = catalogCache.getDirectory()

        assertThat(first.map { it.id }).containsExactly(centerId, 5140)
        assertThat(second).isEqualTo(first)
        assertThat(second.first { it.id == centerId }.tzData).isEqualTo("America/Los_Angeles")
        // The second read came from Redis, not a second API call.
        Mockito.verify(globalEntryApi, Mockito.times(1)).getLocations(any(), any(), any(), any())
    }

    // --- provider access + auth response ---

    @Test
    fun `enabledProviders reflects whether Global Entry access is granted`() {
        val before = registerAndLogin()
        assertThat(getJson("/api/auth/me", before).get("enabledProviders").values().map { it.asString() }).doesNotContain("GLOBAL_ENTRY")

        enableGlobalEntry()

        assertThat(getJson("/api/auth/me", before).get("enabledProviders").values().map { it.asString() }).contains("GLOBAL_ENTRY", "RECREATION_GOV")
    }

    @Test
    fun `creating a Global Entry request is refused while the provider is disabled`() {
        val session = userWithPhone()

        val result = doPost("/api/search-requests", session, createBody())

        assertThat(result.response.status).isGreaterThanOrEqualTo(400)
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM search_requests", Long::class.java)).isEqualTo(0L)
    }

    // --- create / read / update ---

    @Test
    fun `create persists the window, fixed nights and group size, and the center's name and timezone`() {
        enableGlobalEntry()
        val session = userWithPhone()

        val result = doPost("/api/search-requests", session, createBody())

        assertThat(result.response.status).isEqualTo(201)
        val body = mapper.readTree(result.response.contentAsString)
        assertThat(body.get("nights").asInt()).isEqualTo(1)
        assertThat(body.get("groupSize").asInt()).isEqualTo(1)
        assertThat(body.get("campgroundName").asString()).isEqualTo("SF Enrollment Center — San Francisco, CA")
        assertThat(body.get("earliestTime").asString()).isEqualTo("11:30")
        assertThat(body.get("latestTime").asString()).isEqualTo("14:00")
        assertThat(body.get("provider").get("type").asString()).isEqualTo("GLOBAL_ENTRY")

        val row = jdbcTemplate.queryForMap("SELECT provider, nights, group_size, campground_timezone FROM search_requests")
        assertThat(row["provider"]).isEqualTo("GLOBAL_ENTRY")
        assertThat(row["campground_timezone"]).isEqualTo("America/Los_Angeles")
        val details = jdbcTemplate.queryForMap("SELECT earliest_time::text AS e, latest_time::text AS l FROM global_entry_search_request_details")
        assertThat(details["e"]).isEqualTo("11:30:00")
        assertThat(details["l"]).isEqualTo("14:00:00")
    }

    @Test
    fun `create rejects an inverted window and an unknown center`() {
        enableGlobalEntry()
        val session = userWithPhone()

        assertThat(doPost("/api/search-requests", session, createBody(earliestTime = "14:00", latestTime = "11:30")).response.status).isEqualTo(400)
        assertThat(doPost("/api/search-requests", session, createBody().copy(campsiteId = 1)).response.status).isEqualTo(404)
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM search_requests", Long::class.java)).isEqualTo(0L)
    }

    @Test
    fun `update keeps the request and clears an omitted bound`() {
        enableGlobalEntry()
        val session = userWithPhone()
        val id = extractId(doPost("/api/search-requests", session, createBody()))

        val result = doPut(
            "/api/search-requests/$id",
            session,
            UpdateSearchRequestBody(startDay = day, nights = 1, groupSize = 1, campsiteId = centerId, name = "renamed", completed = false, earliestTime = "12:00"),
        )

        assertThat(result.response.status).isEqualTo(200)
        val body = mapper.readTree(result.response.contentAsString)
        assertThat(body.get("earliestTime").asString()).isEqualTo("12:00")
        assertThat(body.get("latestTime").isNull).isTrue()
        assertThat(body.get("name").asString()).isEqualTo("renamed")
    }

    @Test
    fun `soft deleting a Global Entry request keeps its details row`() {
        enableGlobalEntry()
        val session = userWithPhone()
        val id = extractId(doPost("/api/search-requests", session, createBody()))

        assertThat(doDelete("/api/search-requests/$id", session).response.status).isEqualTo(204)

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM global_entry_search_request_details", Long::class.java)).isEqualTo(1L)
    }

    // --- poll check end to end ---

    @Test
    fun `a poll check persists the earliest slot and count, and a count change does not queue another alert`() {
        enableGlobalEntry()
        val session = userWithPhone()
        val id = extractId(doPost("/api/search-requests", session, createBody()))

        stubSlots("${day}T10:10", "${day}T11:40", "${day}T12:20")
        pollCheckService.check(com.davismariotti.campalert.provider.Provider.GLOBAL_ENTRY, centerId)

        var json = getJson("/api/search-requests/$id", session)
        assertThat(json.get("matchedStartDay").asString()).isEqualTo(day.toString())
        assertThat(json.get("matchedStartTime").asString()).isEqualTo("11:40")
        assertThat(json.get("matchedSlotCount").asInt()).isEqualTo(2)
        assertThat(outboxTypes()).containsExactly("AVAILABLE")

        stubSlots("${day}T11:40", "${day}T12:20", "${day}T13:00")
        pollCheckService.check(com.davismariotti.campalert.provider.Provider.GLOBAL_ENTRY, centerId)

        json = getJson("/api/search-requests/$id", session)
        assertThat(json.get("matchedSlotCount").asInt()).isEqualTo(3)
        assertThat(outboxTypes()).containsExactly("AVAILABLE")

        stubSlots("${day}T09:00")
        pollCheckService.check(com.davismariotti.campalert.provider.Provider.GLOBAL_ENTRY, centerId)

        json = getJson("/api/search-requests/$id", session)
        assertThat(json.get("matchedStartTime").isNull).isTrue()
        assertThat(json.get("matchedSlotCount").isNull).isTrue()
        assertThat(outboxTypes()).containsExactly("AVAILABLE", "UNAVAILABLE")
    }

    private fun outboxTypes(): List<String> = jdbcTemplate.queryForList("SELECT type FROM notification_outbox ORDER BY id", String::class.java)

    // --- schema ---

    @Test
    fun `the details table rejects an inverted window and a GLOBAL_ENTRY provider row is accepted`() {
        enableGlobalEntry()
        val session = userWithPhone()
        val id = extractId(doPost("/api/search-requests", session, createBody(earliestTime = null, latestTime = null)))

        org.junit.jupiter.api.assertThrows<DataIntegrityViolationException> {
            jdbcTemplate.update("INSERT INTO global_entry_search_request_details (search_request_id, earliest_time, latest_time) VALUES (?, '14:00', '11:30')", id)
        }
    }

    // --- search scoping ---

    @Test
    fun `unscoped campground search excludes Global Entry centers while a scoped search returns them`() {
        val session = registerAndLogin()

        val unscoped = getJson("/api/campground-search?q=san", session)
        val scoped = getJson("/api/campground-search?q=san&provider=GLOBAL_ENTRY", session)

        assertThat(unscoped.values().map { it.get("provider").get("type").asString() }).doesNotContain("GLOBAL_ENTRY")
        assertThat(scoped.values().map { it.get("id").asInt() }.toSet()).isEqualTo(setOf(centerId, 5140))
    }
}
