package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.model.PermitSearchRequest
import com.davismariotti.campalert.model.PermitSearchRequestState
import com.davismariotti.campalert.model.PermitZoneTarget
import com.davismariotti.campalert.model.SearchType
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.repository.PermitSearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.scheduling.UserAvailabilityProcessedEvent
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PermitPollCheckServiceTest {
    private val permitSearchRequestRepository = mock(PermitSearchRequestRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val provider = Provider.RECREATION_GOV
    private val permitAvailabilityMatcher = mock(PermitAvailabilityProvider::class.java).also {
        `when`(it.provider).thenReturn(provider)
    }
    private val registry = PermitAvailabilityProviderRegistry(listOf(permitAvailabilityMatcher))
    private val permitAvailabilityStateService = mock(PermitAvailabilityStateService::class.java)
    private val eventPublisher = mock(ApplicationEventPublisher::class.java)

    private val service = PermitPollCheckService(
        permitSearchRequestRepository,
        userRepository,
        registry,
        permitAvailabilityStateService,
        eventPublisher,
    )

    private val permitId = "233261"
    private val futureDay = LocalDate.now(ZoneOffset.UTC).plusDays(1)
    private val normalUser = User(id = 1L, email = "user@example.com", passwordHash = "hash")

    private fun zoneRequest(
        id: Long = 1L,
        userId: Long? = 1L,
        pauseReason: String? = null,
        endDay: LocalDate = futureDay,
    ): PermitSearchRequest {
        val req = PermitSearchRequest(
            id = id,
            permitId = permitId,
            permitName = "Desolation",
            groupSize = 2,
            name = "test",
            userId = userId,
            searchType = SearchType.ZONE,
            provider = provider,
        )
        val st = PermitSearchRequestState()
        st.permitSearchRequest = req
        st.pauseReason = pauseReason
        req.state = st
        val target = PermitZoneTarget()
        target.permitSearchRequest = req
        target.divisionIds = listOf("290")
        target.startDay = futureDay
        target.endDay = endDay
        req.zoneTarget = target
        return req
    }

    private fun result(request: PermitSearchRequest, hasAvailability: Boolean = false) = PermitAvailabilityResult(request = request, hasAvailability = hasAvailability)

    @Test
    fun `paused request is not processed`() {
        val req = zoneRequest(pauseReason = "SYSTEM_PAUSED")
        `when`(permitSearchRequestRepository.findByPermitIdAndProviderAndCompletedFalse(permitId, provider)).thenReturn(listOf(req))

        val evaluated = service.check(provider, permitId)

        assertEquals(0, evaluated)
        verify(permitAvailabilityMatcher, never()).check(any(), any(), any(), any())
    }

    @Test
    fun `request without userId is not processed`() {
        val req = zoneRequest(userId = null)
        `when`(permitSearchRequestRepository.findByPermitIdAndProviderAndCompletedFalse(permitId, provider)).thenReturn(listOf(req))

        val evaluated = service.check(provider, permitId)

        assertEquals(0, evaluated)
        verify(permitAvailabilityMatcher, never()).check(any(), any(), any(), any())
    }

    @Test
    fun `empty request list causes early return`() {
        `when`(permitSearchRequestRepository.findByPermitIdAndProviderAndCompletedFalse(permitId, provider)).thenReturn(emptyList())

        val evaluated = service.check(provider, permitId)

        assertEquals(0, evaluated)
        verify(permitAvailabilityStateService, never()).processUserResults(any(), any())
    }

    @Test
    fun `result is passed to processUserResults and event published`() {
        val req = zoneRequest()
        `when`(permitSearchRequestRepository.findByPermitIdAndProviderAndCompletedFalse(permitId, provider)).thenReturn(listOf(req))
        `when`(userRepository.findAllById(any())).thenReturn(listOf(normalUser))
        `when`(permitAvailabilityMatcher.check(any(), any(), any(), any())).thenReturn(result(req))

        val evaluated = service.check(provider, permitId)

        assertEquals(1, evaluated)
        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<PermitAvailabilityResult>>
        verify(permitAvailabilityStateService).processUserResults(capture(captor), any())
        assertEquals(1, captor.value.size)

        val eventCaptor = ArgumentCaptor.forClass(Any::class.java)
        verify(eventPublisher).publishEvent(eventCaptor.capture())
        assertTrue(eventCaptor.value is UserAvailabilityProcessedEvent)
        assertEquals(1L, (eventCaptor.value as UserAvailabilityProcessedEvent).userId)
    }

    @Test
    fun `exception in matcher check - processUserResults still called with empty list and event published`() {
        val req = zoneRequest()
        `when`(permitSearchRequestRepository.findByPermitIdAndProviderAndCompletedFalse(permitId, provider)).thenReturn(listOf(req))
        `when`(userRepository.findAllById(any())).thenReturn(listOf(normalUser))
        `when`(permitAvailabilityMatcher.check(any(), any(), any(), any())).thenThrow(RuntimeException("Recreation.gov unavailable"))

        service.check(provider, permitId)

        @Suppress("UNCHECKED_CAST")
        val stateCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<PermitAvailabilityResult>>
        verify(permitAvailabilityStateService).processUserResults(capture(stateCaptor), any())
        assertTrue(stateCaptor.value.isEmpty())

        val eventCaptor = ArgumentCaptor.forClass(Any::class.java)
        verify(eventPublisher).publishEvent(eventCaptor.capture())
        assertTrue(eventCaptor.value is UserAvailabilityProcessedEvent)
    }

    @Test
    fun `exception in processUserResults - event still published`() {
        val req = zoneRequest()
        `when`(permitSearchRequestRepository.findByPermitIdAndProviderAndCompletedFalse(permitId, provider)).thenReturn(listOf(req))
        `when`(userRepository.findAllById(any())).thenReturn(listOf(normalUser))
        `when`(permitAvailabilityMatcher.check(any(), any(), any(), any())).thenReturn(result(req))
        doThrow(RuntimeException("DB failure")).`when`(permitAvailabilityStateService).processUserResults(any(), any())

        service.check(provider, permitId)

        val eventCaptor = ArgumentCaptor.forClass(Any::class.java)
        verify(eventPublisher).publishEvent(eventCaptor.capture())
        assertTrue(eventCaptor.value is UserAvailabilityProcessedEvent)
    }

    @Test
    fun `past-window request is auto-completed and not evaluated`() {
        val yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1)
        val req = zoneRequest(endDay = yesterday)
        `when`(permitSearchRequestRepository.findByPermitIdAndProviderAndCompletedFalse(permitId, provider)).thenReturn(listOf(req))

        val evaluated = service.check(provider, permitId)

        assertEquals(0, evaluated)
        assertTrue(req.state.completed)
        verify(permitSearchRequestRepository).save(req)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> any(): T = org.mockito.Mockito.any<T>() as T

    @Suppress("UNCHECKED_CAST")
    private fun <T> capture(captor: ArgumentCaptor<T>): T {
        captor.capture()
        return null as T
    }
}
