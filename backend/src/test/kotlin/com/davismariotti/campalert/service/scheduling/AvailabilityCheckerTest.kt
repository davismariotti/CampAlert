package com.davismariotti.campalert.service.scheduling

import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.recreation.Campground
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.availability.AvailabilityResult
import com.davismariotti.campalert.service.availability.RecreationService
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

class AvailabilityCheckerTest {
    private val searchRequestRepository = mock(SearchRequestRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val recreationService = mock(RecreationService::class.java)
    private val availabilityStateService = mock(AvailabilityStateService::class.java)
    private val eventPublisher = mock(ApplicationEventPublisher::class.java)

    private val executor = java.util.concurrent.Executors
        .newSingleThreadExecutor()

    private val checker = AvailabilityChecker(
        searchRequestRepository,
        userRepository,
        recreationService,
        availabilityStateService,
        eventPublisher,
        executor,
    )

    private val futureDay = LocalDate.now(ZoneOffset.UTC).plusDays(1)

    private val normalUser = User(
        id = 1L,
        email = "user@example.com",
        passwordHash = "hash",
    )

    private val pushoverUser = User(
        id = 2L,
        email = "pushover@example.com",
        passwordHash = "hash",
        pushoverOverrideEnabled = true,
        pushoverApiToken = "token",
        pushoverUserKey = "key",
    )

    private fun request(
        id: Long = 1L,
        userId: Long? = 1L,
        pauseReason: String? = null,
        startDay: LocalDate = futureDay,
    ) = SearchRequest(
        id = id,
        startDay = startDay,
        nights = 1,
        groupSize = 2,
        campsiteId = 233359,
        name = "test",
        completed = false,
        userId = userId,
        pauseReason = pauseReason,
    )

    private fun result(request: SearchRequest, hasAvailableSites: Boolean = false) =
        AvailabilityResult(
            searchRequest = request,
            campground = Campground(emptyMap()),
            hasAvailableSites = hasAvailableSites,
        )

    // ---- Filtering ----

    @Test
    fun `paused request is not processed`() {
        val req = request(pauseReason = "SYSTEM_PAUSED")
        `when`(searchRequestRepository.findByCompletedFalse()).thenReturn(listOf(req))
        `when`(userRepository.findAllById(any())).thenReturn(emptyList<User>())

        checker.processSearchRequests()

        verify(recreationService, never()).checkAvailability(any(), any(), any())
    }

    @Test
    fun `request without userId is not processed`() {
        val req = request(userId = null)
        `when`(searchRequestRepository.findByCompletedFalse()).thenReturn(listOf(req))

        checker.processSearchRequests()

        verify(recreationService, never()).checkAvailability(any(), any(), any())
    }

    @Test
    fun `empty request list causes early return`() {
        `when`(searchRequestRepository.findByCompletedFalse()).thenReturn(emptyList())

        checker.processSearchRequests()

        verify(recreationService, never()).checkAvailability(any(), any(), any())
        verify(availabilityStateService, never()).processUserResults(any(), any())
    }

    @Test
    fun `user not found in repository causes early return`() {
        val req = request()
        `when`(searchRequestRepository.findByCompletedFalse()).thenReturn(listOf(req))
        `when`(userRepository.findAllById(any())).thenReturn(emptyList<User>())

        checker.processSearchRequests()

        verify(recreationService, never()).checkAvailability(any(), any(), any())
        verify(availabilityStateService, never()).processUserResults(any(), any())
    }

    // ---- Non-pushover path ----

    @Test
    fun `non-pushover result is passed to processUserResults`() {
        val req = request()
        `when`(searchRequestRepository.findByCompletedFalse()).thenReturn(listOf(req))
        `when`(userRepository.findAllById(any())).thenReturn(listOf(normalUser))
        `when`(recreationService.checkAvailability(any(), any(), any())).thenReturn(result(req))

        checker.processSearchRequests()

        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<AvailabilityResult>>
        verify(availabilityStateService).processUserResults(capture(captor), any())
        assertEquals(1, captor.value.size)
        assertEquals(req, captor.value[0].searchRequest)
    }

    @Test
    fun `UserAvailabilityProcessedEvent published for non-pushover user`() {
        val req = request()
        `when`(searchRequestRepository.findByCompletedFalse()).thenReturn(listOf(req))
        `when`(userRepository.findAllById(any())).thenReturn(listOf(normalUser))
        `when`(recreationService.checkAvailability(any(), any(), any())).thenReturn(result(req))

        checker.processSearchRequests()

        val captor = ArgumentCaptor.forClass(Any::class.java)
        verify(eventPublisher).publishEvent(captor.capture())
        assertTrue(captor.value is UserAvailabilityProcessedEvent)
        assertEquals(1L, (captor.value as UserAvailabilityProcessedEvent).userId)
    }

    @Test
    fun `multi-request user processUserResults called once with all results`() {
        val req1 = request(id = 1)
        val req2 = request(id = 2)
        `when`(searchRequestRepository.findByCompletedFalse()).thenReturn(listOf(req1, req2))
        `when`(userRepository.findAllById(any())).thenReturn(listOf(normalUser))
        `when`(recreationService.checkAvailability(any(), any(), any()))
            .thenReturn(result(req1))
            .thenReturn(result(req2))

        checker.processSearchRequests()

        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<AvailabilityResult>>
        verify(availabilityStateService).processUserResults(capture(captor), any())
        assertEquals(2, captor.value.size)
    }

    // ---- Pushover path (now routes through outbox like SMS) ----

    @Test
    fun `pushover user result is passed to processUserResults`() {
        val req = request(userId = 2L)
        `when`(searchRequestRepository.findByCompletedFalse()).thenReturn(listOf(req))
        `when`(userRepository.findAllById(any())).thenReturn(listOf(pushoverUser))
        `when`(recreationService.checkAvailability(any(), any(), any()))
            .thenReturn(result(req, hasAvailableSites = true))

        checker.processSearchRequests()

        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<AvailabilityResult>>
        verify(availabilityStateService).processUserResults(capture(captor), any())
        assertEquals(1, captor.value.size)
    }

    @Test
    fun `UserAvailabilityProcessedEvent published for pushover user`() {
        val req = request(userId = 2L)
        `when`(searchRequestRepository.findByCompletedFalse()).thenReturn(listOf(req))
        `when`(userRepository.findAllById(any())).thenReturn(listOf(pushoverUser))
        `when`(recreationService.checkAvailability(any(), any(), any()))
            .thenReturn(result(req, hasAvailableSites = true))

        checker.processSearchRequests()

        val captor = ArgumentCaptor.forClass(Any::class.java)
        verify(eventPublisher).publishEvent(captor.capture())
        assertTrue(captor.value is UserAvailabilityProcessedEvent)
        assertEquals(2L, (captor.value as UserAvailabilityProcessedEvent).userId)
    }

    // ---- Exception handling ----

    @Test
    fun `exception in checkAvailability - processUserResults called with empty list and event published`() {
        val req = request()
        `when`(searchRequestRepository.findByCompletedFalse()).thenReturn(listOf(req))
        `when`(userRepository.findAllById(any())).thenReturn(listOf(normalUser))
        `when`(recreationService.checkAvailability(any(), any(), any()))
            .thenThrow(RuntimeException("RIDB unavailable"))

        checker.processSearchRequests()

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
        `when`(searchRequestRepository.findByCompletedFalse()).thenReturn(listOf(req))
        `when`(userRepository.findAllById(any())).thenReturn(listOf(normalUser))
        `when`(recreationService.checkAvailability(any(), any(), any())).thenReturn(result(req))
        doThrow(RuntimeException("DB failure"))
            .`when`(availabilityStateService)
            .processUserResults(any(), any())

        checker.processSearchRequests()

        val captor = ArgumentCaptor.forClass(Any::class.java)
        verify(eventPublisher).publishEvent(captor.capture())
        assertTrue(captor.value is UserAvailabilityProcessedEvent)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> any(): T = org.mockito.Mockito.any<T>() as T

    @Suppress("UNCHECKED_CAST")
    private fun <T> capture(captor: ArgumentCaptor<T>): T {
        captor.capture()
        return null as T
    }
}
