package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.model.CreateSearchRequestBody
import com.davismariotti.campalert.api.model.UpdateSearchRequestBody
import com.davismariotti.campalert.exception.FlexibleSearchValidationException
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.SearchRequestState
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.provider.camplife.CampLifeCatalogCache
import com.davismariotti.campalert.provider.reservecalifornia.ReserveCaliforniaCatalogCache
import com.davismariotti.campalert.provider.reservecalifornia.ReserveCaliforniaOccupancyService
import com.davismariotti.campalert.repository.NotificationOutboxRepository
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.TimezoneResolutionService
import com.davismariotti.campalert.service.scheduling.PollTargetRegistrationService
import com.davismariotti.campalert.service.scheduling.ProviderSearchWindowProperties
import com.davismariotti.campalert.service.turnstile.TurnstileFailedException
import com.davismariotti.campalert.service.turnstile.TurnstileService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDate
import com.davismariotti.campalert.api.model.Provider as ApiProvider
import com.davismariotti.campalert.api.model.ProviderType as ApiProviderType

class SearchRequestsDelegateImplTest {
    private val searchRequestRepository = mock(SearchRequestRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val phoneNumberRepository = mock(PhoneNumberRepository::class.java)
    private val notificationOutboxRepository = mock(NotificationOutboxRepository::class.java)
    private val timezoneResolutionService = mock(TimezoneResolutionService::class.java)
    private val pollTargetRegistrationService = mock(PollTargetRegistrationService::class.java)
    private val campLifeCatalogCache = mock(CampLifeCatalogCache::class.java)
    private val reserveCaliforniaCatalogCache = mock(ReserveCaliforniaCatalogCache::class.java)
    private val reserveCaliforniaOccupancyService = mock(ReserveCaliforniaOccupancyService::class.java)
    private val providerSearchWindowProperties = mock(ProviderSearchWindowProperties::class.java).also {
        `when`(it.maxRangeWidthDaysFor(Provider.RECREATION_GOV)).thenReturn(30)
        `when`(it.maxRangeWidthDaysFor(Provider.CAMPLIFE)).thenReturn(9)
    }
    private val turnstileService = mock(TurnstileService::class.java)

    private val delegate = SearchRequestsDelegateImpl(
        searchRequestRepository,
        userRepository,
        phoneNumberRepository,
        notificationOutboxRepository,
        timezoneResolutionService,
        pollTargetRegistrationService,
        campLifeCatalogCache,
        reserveCaliforniaCatalogCache,
        reserveCaliforniaOccupancyService,
        providerSearchWindowProperties,
        turnstileService,
    )

    private val user = User(id = 1L, email = "user@example.com", passwordHash = "hash")

    @BeforeEach
    fun setUp() {
        val auth = UsernamePasswordAuthenticationToken(user.email, null, listOf(SimpleGrantedAuthority("ROLE_USER")))
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = auth
        SecurityContextHolder.setContext(context)
        `when`(userRepository.findByEmail(user.email)).thenReturn(user)
        `when`(phoneNumberRepository.countByUserIdAndStatus(user.id!!, PhoneNumberStatus.VERIFIED)).thenReturn(1L)
        `when`(searchRequestRepository.save(anyKt())).thenAnswer { invocation ->
            val req = invocation.arguments[0] as SearchRequest
            val saved = if (req.id == null) req.copy(id = 100L) else req
            saved.state = req.state
            saved
        }
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun createBody(provider: ApiProvider? = null) =
        CreateSearchRequestBody(
            startDay = LocalDate.now().plusDays(5),
            nights = 2,
            groupSize = 2,
            campsiteId = 233359,
            campgroundName = "Test Campground",
            name = "Test",
            provider = provider,
            turnstileToken = "test-token",
        )

    @Test
    fun `creating without a provider defaults to RECREATION_GOV`() {
        val result = delegate.createSearchRequest(createBody())

        assertEquals(ApiProviderType.RECREATION_GOV, result.body!!.provider.type)
        assertEquals("Recreation.gov", result.body!!.provider.name)
        verify(pollTargetRegistrationService).ensureCampgroundTarget(233359, Provider.RECREATION_GOV)
    }

    @Test
    fun `createSearchRequest throws TurnstileFailedException and never registers a poll target when verification fails`() {
        `when`(turnstileService.verify(org.mockito.ArgumentMatchers.anyString())).thenThrow(TurnstileFailedException())

        assertThrows(TurnstileFailedException::class.java) { delegate.createSearchRequest(createBody()) }

        verify(pollTargetRegistrationService, org.mockito.Mockito.never())
            .ensureCampgroundTarget(org.mockito.ArgumentMatchers.anyInt(), anyKt())
        verify(searchRequestRepository, org.mockito.Mockito.never()).save(anyKt())
    }

    @Test
    fun `creating with an explicit provider honors it`() {
        val result = delegate.createSearchRequest(createBody(provider = ApiProvider(type = ApiProviderType.RECREATION_GOV, name = "ignored")))

        assertEquals(ApiProviderType.RECREATION_GOV, result.body!!.provider.type)
        assertEquals("Recreation.gov", result.body!!.provider.name)
        verify(pollTargetRegistrationService).ensureCampgroundTarget(233359, Provider.RECREATION_GOV)
    }

    @Test
    fun `creating a ReserveCalifornia any-site groupSize search registers the poll target immediately, regardless of occupancy warm-up state`() {
        // D18: registration must never be gated on occupancy warm-up completing — this is the
        // regression guard for that decision, since it would be easy for a future change to
        // reintroduce a gate here without realizing it breaks concurrent site_ids-scoped searches
        // against the same facility (see reserve-california-unit-occupancy-warmup spec).
        `when`(reserveCaliforniaCatalogCache.getDirectory()).thenReturn(
            listOf(
                com.davismariotti.campalert.provider.reservecalifornia
                    .ReserveCaliforniaDirectoryEntry(facilityId = 233359, facilityName = "Test", placeId = 683, placeName = "Test Park")
            ),
        )
        `when`(reserveCaliforniaCatalogCache.getFacilityRoster(233359)).thenReturn(null)

        val result = delegate.createSearchRequest(
            createBody(provider = ApiProvider(type = ApiProviderType.RESERVE_CALIFORNIA, name = "ignored")).copy(groupSize = 6),
        )

        assertEquals(ApiProviderType.RESERVE_CALIFORNIA, result.body!!.provider.type)
        verify(pollTargetRegistrationService).ensureCampgroundTarget(233359, Provider.RESERVE_CALIFORNIA)
    }

    @Test
    fun `updating without a provider keeps the existing value`() {
        val existing = existingRequest()
        `when`(searchRequestRepository.findById(10L)).thenReturn(java.util.Optional.of(existing))

        val result = delegate.updateSearchRequest(
            10L,
            UpdateSearchRequestBody(
                startDay = existing.startDay,
                nights = existing.nights,
                groupSize = existing.groupSize,
                campsiteId = existing.campsiteId,
                name = existing.name,
                completed = false,
            ),
        )

        assertEquals(ApiProviderType.RECREATION_GOV, result.body!!.provider.type)
    }

    @Test
    fun `creating with a valid flexible range is accepted and mapped back`() {
        val startDay = LocalDate.now().plusDays(5)
        val latestStartDay = startDay.plusDays(10)
        val result = delegate.createSearchRequest(createBody().copy(latestStartDay = latestStartDay))

        assertEquals(201, result.statusCode.value())
        assertEquals(latestStartDay, result.body!!.latestStartDay)
        assertNull(result.body!!.matchedStartDay)
        assertNull(result.body!!.matchedEndDay)
    }

    @Test
    fun `creating with latestStartDay earlier than startDay is rejected`() {
        val startDay = LocalDate.now().plusDays(5)
        assertThrows(FlexibleSearchValidationException.LatestStartDayTooEarly::class.java) {
            delegate.createSearchRequest(createBody().copy(startDay = startDay, latestStartDay = startDay.minusDays(1)))
        }
        verify(searchRequestRepository, org.mockito.Mockito.never()).save(anyKt())
    }

    @Test
    fun `creating with latestStartDay equal to startDay is accepted as a single-candidate flex request`() {
        val startDay = LocalDate.now().plusDays(5)
        val result = delegate.createSearchRequest(createBody().copy(startDay = startDay, latestStartDay = startDay))

        assertEquals(201, result.statusCode.value())
    }

    @Test
    fun `creating with a range wider than the provider max is rejected`() {
        val startDay = LocalDate.now().plusDays(5)
        assertThrows(FlexibleSearchValidationException.RangeTooWide::class.java) {
            delegate.createSearchRequest(createBody().copy(startDay = startDay, latestStartDay = startDay.plusDays(31)))
        }
    }

    @Test
    fun `creating with latestStartDay for a provider with no configured max is rejected`() {
        `when`(providerSearchWindowProperties.maxRangeWidthDaysFor(Provider.CAMPLIFE)).thenReturn(null)
        val startDay = LocalDate.now().plusDays(5)
        assertThrows(FlexibleSearchValidationException.Unsupported::class.java) {
            delegate.createSearchRequest(
                createBody(provider = ApiProvider(type = ApiProviderType.CAMPLIFE, name = "CampLife"))
                    .copy(startDay = startDay, latestStartDay = startDay.plusDays(4)),
            )
        }
    }

    @Test
    fun `updating to clear latestStartDay reverts to an exact-date search`() {
        val existing = existingRequest(latestStartDay = existingRequest().startDay.plusDays(5))
        `when`(searchRequestRepository.findById(10L)).thenReturn(java.util.Optional.of(existing))

        val result = delegate.updateSearchRequest(
            10L,
            UpdateSearchRequestBody(
                startDay = existing.startDay,
                nights = existing.nights,
                groupSize = existing.groupSize,
                campsiteId = existing.campsiteId,
                name = existing.name,
                completed = false,
                latestStartDay = null,
            ),
        )

        assertEquals(200, result.statusCode.value())
        assertNull(result.body!!.latestStartDay)
    }

    @Test
    fun `response exposes matched dates from state`() {
        val existing = existingRequest()
        existing.state.matchedStartDay = existing.startDay.plusDays(1)
        existing.state.matchedEndDay = existing.startDay.plusDays(3)
        `when`(searchRequestRepository.findById(10L)).thenReturn(java.util.Optional.of(existing))

        val result = delegate.getSearchRequest(10L)

        assertEquals(existing.startDay.plusDays(1), result.body!!.matchedStartDay)
        assertEquals(existing.startDay.plusDays(3), result.body!!.matchedEndDay)
    }

    private fun existingRequest(latestStartDay: LocalDate? = null): SearchRequest {
        val req = SearchRequest(
            id = 10L,
            startDay = LocalDate.now().plusDays(5),
            nights = 2,
            groupSize = 2,
            campsiteId = 233359,
            name = "Test",
            userId = user.id,
            provider = Provider.RECREATION_GOV,
            latestStartDay = latestStartDay,
        )
        val state = SearchRequestState()
        state.searchRequest = req
        req.state = state
        return req
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyKt(): T = org.mockito.ArgumentMatchers.any<T>() as T
}
