package com.davismariotti.campalert.service.scheduling

import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.SearchRequestState
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.availability.AvailabilityResult
import com.davismariotti.campalert.service.availability.CampgroundAvailabilityProvider
import com.davismariotti.campalert.service.availability.CampgroundAvailabilityProviderRegistry
import com.davismariotti.campalert.service.state.AvailabilityStateService
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

class CampgroundPollCheckServiceTest {
    private val searchRequestRepository = mock(SearchRequestRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val provider = Provider.RECREATION_GOV
    private val recreationService = mock(CampgroundAvailabilityProvider::class.java).also {
        `when`(it.provider).thenReturn(provider)
    }
    private val registry = CampgroundAvailabilityProviderRegistry(listOf(recreationService))
    private val availabilityStateService = mock(AvailabilityStateService::class.java)
    private val eventPublisher = mock(ApplicationEventPublisher::class.java)

    private val service = CampgroundPollCheckService(
        searchRequestRepository,
        userRepository,
        registry,
        availabilityStateService,
        eventPublisher,
    )

    private val campsiteId = 233359
    private val futureDay = LocalDate.now(ZoneOffset.UTC).plusDays(1)

    private val normalUser = User(id = 1L, email = "user@example.com", passwordHash = "hash")

    private fun request(
        id: Long = 1L,
        userId: Long? = 1L,
        pauseReason: String? = null,
        startDay: LocalDate = futureDay,
    ): SearchRequest {
        val req = SearchRequest(
            id = id,
            startDay = startDay,
            nights = 1,
            groupSize = 2,
            campsiteId = campsiteId,
            name = "test",
            userId = userId,
            provider = provider,
        )
        val st = SearchRequestState()
        st.searchRequest = req
        st.searchRequestId = id
        st.pauseReason = pauseReason
        req.state = st
        return req
    }

    private fun result(request: SearchRequest, hasAvailableSites: Boolean = false) = AvailabilityResult(searchRequest = request, hasAvailableSites = hasAvailableSites, availableSiteCount = if (hasAvailableSites) 1 else 0)

    @Test
    fun `paused request is not processed`() {
        val req = request(pauseReason = "SYSTEM_PAUSED")
        `when`(searchRequestRepository.findByCampsiteIdAndProviderAndCompletedFalse(campsiteId, provider)).thenReturn(listOf(req))

        val evaluated = service.check(provider, campsiteId)

        assertEquals(0, evaluated)
        verify(recreationService, never()).checkAvailability(any(), any(), any())
    }

    @Test
    fun `request without userId is not processed`() {
        val req = request(userId = null)
        `when`(searchRequestRepository.findByCampsiteIdAndProviderAndCompletedFalse(campsiteId, provider)).thenReturn(listOf(req))

        val evaluated = service.check(provider, campsiteId)

        assertEquals(0, evaluated)
        verify(recreationService, never()).checkAvailability(any(), any(), any())
    }

    @Test
    fun `empty request list causes early return`() {
        `when`(searchRequestRepository.findByCampsiteIdAndProviderAndCompletedFalse(campsiteId, provider)).thenReturn(emptyList())

        val evaluated = service.check(provider, campsiteId)

        assertEquals(0, evaluated)
        verify(availabilityStateService, never()).processUserResults(any(), any())
    }

    @Test
    fun `non-pushover result is passed to processUserResults`() {
        val req = request()
        `when`(searchRequestRepository.findByCampsiteIdAndProviderAndCompletedFalse(campsiteId, provider)).thenReturn(listOf(req))
        `when`(userRepository.findAllById(any())).thenReturn(listOf(normalUser))
        `when`(recreationService.checkAvailability(any(), any(), any())).thenReturn(result(req))

        val evaluated = service.check(provider, campsiteId)

        assertEquals(1, evaluated)
        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<AvailabilityResult>>
        verify(availabilityStateService).processUserResults(capture(captor), any())
        assertEquals(1, captor.value.size)
        assertEquals(req, captor.value[0].searchRequest)

        val eventCaptor = ArgumentCaptor.forClass(Any::class.java)
        verify(eventPublisher).publishEvent(eventCaptor.capture())
        assertTrue(eventCaptor.value is UserAvailabilityProcessedEvent)
        assertEquals(1L, (eventCaptor.value as UserAvailabilityProcessedEvent).userId)
    }

    @Test
    fun `multiple requests for same campground share the fetch cache and evaluate together`() {
        val req1 = request(id = 1)
        val req2 = request(id = 2, userId = 2L)
        `when`(searchRequestRepository.findByCampsiteIdAndProviderAndCompletedFalse(campsiteId, provider)).thenReturn(listOf(req1, req2))
        `when`(userRepository.findAllById(any())).thenReturn(listOf(normalUser, User(id = 2L, email = "b@example.com", passwordHash = "h")))
        `when`(recreationService.checkAvailability(any(), any(), any()))
            .thenReturn(result(req1))
            .thenReturn(result(req2))

        val evaluated = service.check(provider, campsiteId)

        assertEquals(2, evaluated)
        verify(availabilityStateService, org.mockito.Mockito.times(2)).processUserResults(any(), any())
    }

    @Test
    fun `exception in checkAvailability - processUserResults still called with empty list and event published`() {
        val req = request()
        `when`(searchRequestRepository.findByCampsiteIdAndProviderAndCompletedFalse(campsiteId, provider)).thenReturn(listOf(req))
        `when`(userRepository.findAllById(any())).thenReturn(listOf(normalUser))
        `when`(recreationService.checkAvailability(any(), any(), any())).thenThrow(RuntimeException("Recreation.gov unavailable"))

        service.check(provider, campsiteId)

        @Suppress("UNCHECKED_CAST")
        val stateCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<AvailabilityResult>>
        verify(availabilityStateService).processUserResults(capture(stateCaptor), any())
        assertTrue(stateCaptor.value.isEmpty())

        val eventCaptor = ArgumentCaptor.forClass(Any::class.java)
        verify(eventPublisher).publishEvent(eventCaptor.capture())
        assertTrue(eventCaptor.value is UserAvailabilityProcessedEvent)
    }

    @Test
    fun `exception in processUserResults - event still published`() {
        val req = request()
        `when`(searchRequestRepository.findByCampsiteIdAndProviderAndCompletedFalse(campsiteId, provider)).thenReturn(listOf(req))
        `when`(userRepository.findAllById(any())).thenReturn(listOf(normalUser))
        `when`(recreationService.checkAvailability(any(), any(), any())).thenReturn(result(req))
        doThrow(RuntimeException("DB failure")).`when`(availabilityStateService).processUserResults(any(), any())

        service.check(provider, campsiteId)

        val eventCaptor = ArgumentCaptor.forClass(Any::class.java)
        verify(eventPublisher).publishEvent(eventCaptor.capture())
        assertTrue(eventCaptor.value is UserAvailabilityProcessedEvent)
    }

    @Test
    fun `past-date request is auto-completed and not evaluated`() {
        val yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1)
        val req = request(startDay = yesterday)
        `when`(searchRequestRepository.findByCampsiteIdAndProviderAndCompletedFalse(campsiteId, provider)).thenReturn(listOf(req))

        val evaluated = service.check(provider, campsiteId)

        assertEquals(0, evaluated)
        assertTrue(req.state.completed)
        verify(searchRequestRepository).save(req)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> any(): T = org.mockito.Mockito.any<T>() as T

    @Suppress("UNCHECKED_CAST")
    private fun <T> capture(captor: ArgumentCaptor<T>): T {
        captor.capture()
        return null as T
    }
}
