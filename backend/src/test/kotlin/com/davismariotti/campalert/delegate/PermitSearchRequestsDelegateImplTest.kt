package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.model.CreatePermitSearchRequestBody
import com.davismariotti.campalert.api.model.PermitType
import com.davismariotti.campalert.api.model.PermitZoneTargetBody
import com.davismariotti.campalert.api.model.UpdatePermitSearchRequestBody
import com.davismariotti.campalert.model.PermitSearchRequest
import com.davismariotti.campalert.model.PermitSearchRequestState
import com.davismariotti.campalert.model.PermitZoneTarget
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.model.SearchType
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.repository.NotificationOutboxRepository
import com.davismariotti.campalert.repository.PermitSearchRequestRepository
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.permit.PermitClassificationService
import com.davismariotti.campalert.service.permit.PermitContentCache
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
import java.util.Optional
import com.davismariotti.campalert.api.model.Provider as ApiProvider
import com.davismariotti.campalert.api.model.ProviderType as ApiProviderType

class PermitSearchRequestsDelegateImplTest {
    private val permitSearchRequestRepository = mock(PermitSearchRequestRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val phoneNumberRepository = mock(PhoneNumberRepository::class.java)
    private val notificationOutboxRepository = mock(NotificationOutboxRepository::class.java)
    private val permitClassificationService = mock(PermitClassificationService::class.java)
    private val permitContentCache = mock(PermitContentCache::class.java)
    private val pollTargetRegistrationService = mock(PollTargetRegistrationService::class.java)

    private val delegate = PermitSearchRequestsDelegateImpl(
        permitSearchRequestRepository,
        userRepository,
        phoneNumberRepository,
        notificationOutboxRepository,
        permitClassificationService,
        permitContentCache,
        pollTargetRegistrationService,
    )

    private val user = User(id = 1L, email = "user@example.com", passwordHash = "hash")
    private val permitId = "233261"

    @BeforeEach
    fun setUp() {
        val auth = UsernamePasswordAuthenticationToken(user.email, null, listOf(SimpleGrantedAuthority("ROLE_USER")))
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = auth
        SecurityContextHolder.setContext(context)
        `when`(userRepository.findByEmail(user.email)).thenReturn(user)
        `when`(phoneNumberRepository.countByUserIdAndStatus(user.id!!, PhoneNumberStatus.VERIFIED)).thenReturn(1L)
        `when`(permitClassificationService.classify(permitId)).thenReturn(SearchType.ZONE)
        `when`(permitSearchRequestRepository.save(anyKt())).thenAnswer { invocation ->
            val req = invocation.arguments[0] as PermitSearchRequest
            val saved = if (req.id == null) req.copy(id = 100L) else req
            saved.state = req.state
            saved.zoneTarget = req.zoneTarget
            saved.itineraryTarget = req.itineraryTarget
            saved
        }
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun createBody(provider: ApiProvider? = null) =
        CreatePermitSearchRequestBody(
            permitId = permitId,
            permitName = "Desolation",
            groupSize = 2,
            name = "Test",
            searchType = PermitType.ZONE,
            zoneTarget = PermitZoneTargetBody(divisionIds = listOf("290"), startDay = LocalDate.now().plusDays(1), endDay = LocalDate.now().plusDays(3)),
            provider = provider,
        )

    @Test
    fun `creating without a provider defaults to RECREATION_GOV`() {
        val result = delegate.createPermitSearchRequest(createBody())

        assertEquals(ApiProviderType.RECREATION_GOV, result.body!!.provider.type)
        assertEquals("Recreation.gov", result.body!!.provider.name)
        verify(pollTargetRegistrationService).ensurePermitTarget(permitId, Provider.RECREATION_GOV)
    }

    @Test
    fun `creating with an explicit provider honors it`() {
        val result = delegate.createPermitSearchRequest(createBody(provider = ApiProvider(type = ApiProviderType.RECREATION_GOV, name = "ignored")))

        assertEquals(ApiProviderType.RECREATION_GOV, result.body!!.provider.type)
        assertEquals("Recreation.gov", result.body!!.provider.name)
        verify(pollTargetRegistrationService).ensurePermitTarget(permitId, Provider.RECREATION_GOV)
    }

    @Test
    fun `updating without a provider keeps the existing value`() {
        val existing = existingRequest()
        `when`(permitSearchRequestRepository.findById(10L)).thenReturn(Optional.of(existing))

        val result = delegate.updatePermitSearchRequest(
            10L,
            UpdatePermitSearchRequestBody(
                permitId = permitId,
                permitName = "Desolation",
                groupSize = 2,
                name = "Test",
                searchType = PermitType.ZONE,
                zoneTarget = PermitZoneTargetBody(divisionIds = listOf("290"), startDay = LocalDate.now().plusDays(1), endDay = LocalDate.now().plusDays(3)),
                completed = false,
            ),
        )

        assertEquals(ApiProviderType.RECREATION_GOV, result.body!!.provider.type)
    }

    private fun existingRequest(): PermitSearchRequest {
        val req = PermitSearchRequest(
            id = 10L,
            permitId = permitId,
            permitName = "Desolation",
            groupSize = 2,
            name = "Test",
            userId = user.id,
            searchType = SearchType.ZONE,
            provider = Provider.RECREATION_GOV,
        )
        val state = PermitSearchRequestState()
        state.permitSearchRequest = req
        req.state = state
        val target = PermitZoneTarget()
        target.permitSearchRequest = req
        target.divisionIds = listOf("290")
        target.startDay = LocalDate.now().plusDays(1)
        target.endDay = LocalDate.now().plusDays(3)
        req.zoneTarget = target
        return req
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyKt(): T = org.mockito.ArgumentMatchers.any<T>() as T
}
