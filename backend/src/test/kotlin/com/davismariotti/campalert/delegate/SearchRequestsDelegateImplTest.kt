package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.model.CreateSearchRequestBody
import com.davismariotti.campalert.api.model.UpdateSearchRequestBody
import com.davismariotti.campalert.camplife.CampLifeCatalogCache
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.model.Provider
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.SearchRequestState
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.repository.NotificationOutboxRepository
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.TimezoneResolutionService
import com.davismariotti.campalert.service.scheduling.PollTargetRegistrationService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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

    private val delegate = SearchRequestsDelegateImpl(
        searchRequestRepository,
        userRepository,
        phoneNumberRepository,
        notificationOutboxRepository,
        timezoneResolutionService,
        pollTargetRegistrationService,
        campLifeCatalogCache,
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
        )

    @Test
    fun `creating without a provider defaults to RECREATION_GOV`() {
        val result = delegate.createSearchRequest(createBody())

        assertEquals(ApiProviderType.RECREATION_GOV, result.body!!.provider.type)
        assertEquals("Recreation.gov", result.body!!.provider.name)
        verify(pollTargetRegistrationService).ensureCampgroundTarget(233359, Provider.RECREATION_GOV)
    }

    @Test
    fun `creating with an explicit provider honors it`() {
        val result = delegate.createSearchRequest(createBody(provider = ApiProvider(type = ApiProviderType.RECREATION_GOV, name = "ignored")))

        assertEquals(ApiProviderType.RECREATION_GOV, result.body!!.provider.type)
        assertEquals("Recreation.gov", result.body!!.provider.name)
        verify(pollTargetRegistrationService).ensureCampgroundTarget(233359, Provider.RECREATION_GOV)
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

    private fun existingRequest(): SearchRequest {
        val req = SearchRequest(
            id = 10L,
            startDay = LocalDate.now().plusDays(5),
            nights = 2,
            groupSize = 2,
            campsiteId = 233359,
            name = "Test",
            userId = user.id,
            provider = Provider.RECREATION_GOV,
        )
        val state = SearchRequestState()
        state.searchRequest = req
        req.state = state
        return req
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyKt(): T = org.mockito.ArgumentMatchers.any<T>() as T
}
