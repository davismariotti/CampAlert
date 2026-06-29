package com.davismariotti.campalert.service.state

import com.davismariotti.campalert.model.AvailabilityState
import com.davismariotti.campalert.model.NotificationOutbox
import com.davismariotti.campalert.model.OutboxType
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.SearchRequestState
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.recreation.Campground
import com.davismariotti.campalert.repository.NotificationOutboxRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.service.availability.AvailabilityResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class AvailabilityStateServiceTest {
    private val searchRequestRepository = mock(SearchRequestRepository::class.java)
    private val outboxRepository = mock(NotificationOutboxRepository::class.java)
    private val service = AvailabilityStateService(searchRequestRepository, outboxRepository)

    private val user = User(id = 1L, email = "a@b.com", passwordHash = "x", timezone = "UTC")

    private fun request(
        state: AvailabilityState? = null,
        paused: Boolean = false,
        lastNotified: Instant? = null,
        reminderSent: Instant? = null,
    ): SearchRequest {
        val req = SearchRequest(
            id = 10L,
            startDay = LocalDate.now().plusDays(5),
            nights = 2,
            groupSize = 2,
            campsiteId = 99,
            name = "Test",
            userId = 1L,
        )
        val st = SearchRequestState()
        st.searchRequest = req
        st.searchRequestId = 10L
        st.lastAvailabilityState = state
        st.userPaused = paused
        st.lastNotifiedAt = lastNotified
        st.reminderSentAt = reminderSent
        req.state = st
        return req
    }

    private fun result(request: SearchRequest, available: Boolean) =
        AvailabilityResult(
            searchRequest = request,
            campground = Campground(if (available) mapOf(1 to mockCampsite()) else emptyMap()),
            hasAvailableSites = available,
        )

    private fun mockCampsite() =
        com.davismariotti.campalert.recreation.Campsite(
            campsiteId = 1,
            site = "001",
            loop = "A",
            campsiteReserveType = "SITE_SPECIFIC",
            minimumNumberOfPeople = 1,
            maximumNumberOfPeople = 6,
            availabilities = emptyMap(),
            quantities = emptyMap(),
        )

    @Test
    fun `null to AVAILABLE inserts AVAILABLE outbox row`() {
        val req = request(state = null)
        `when`(searchRequestRepository.save(any(SearchRequest::class.java))).thenAnswer { it.arguments[0] }
        `when`(outboxRepository.save(any(NotificationOutbox::class.java))).thenAnswer { it.arguments[0] }

        service.processUserResults(listOf(result(req, true)), user)

        val captor = ArgumentCaptor.forClass(NotificationOutbox::class.java)
        verify(outboxRepository).save(captor.capture())
        assertEquals(OutboxType.AVAILABLE, captor.value.type)
    }

    @Test
    fun `UNAVAILABLE to AVAILABLE inserts AVAILABLE outbox row`() {
        val req = request(state = AvailabilityState.UNAVAILABLE)
        `when`(searchRequestRepository.save(any(SearchRequest::class.java))).thenAnswer { it.arguments[0] }
        `when`(outboxRepository.save(any(NotificationOutbox::class.java))).thenAnswer { it.arguments[0] }

        service.processUserResults(listOf(result(req, true)), user)

        val captor = ArgumentCaptor.forClass(NotificationOutbox::class.java)
        verify(outboxRepository).save(captor.capture())
        assertEquals(OutboxType.AVAILABLE, captor.value.type)
    }

    @Test
    fun `AVAILABLE to UNAVAILABLE inserts UNAVAILABLE row and clears paused and reminderSentAt`() {
        val req = request(state = AvailabilityState.AVAILABLE, paused = true, reminderSent = Instant.now())
        `when`(outboxRepository.save(any(NotificationOutbox::class.java))).thenAnswer { it.arguments[0] }
        `when`(searchRequestRepository.save(any(SearchRequest::class.java))).thenAnswer { it.arguments[0] }

        service.processUserResults(listOf(result(req, false)), user)

        val outboxCaptor = ArgumentCaptor.forClass(NotificationOutbox::class.java)
        verify(outboxRepository).save(outboxCaptor.capture())
        assertEquals(OutboxType.UNAVAILABLE, outboxCaptor.value.type)

        assertEquals(false, req.state.userPaused)
        assertNull(req.state.reminderSentAt)
    }

    @Test
    fun `AVAILABLE to AVAILABLE with reminder eligibility inserts REMINDER row`() {
        val thirtyOneMinutesAgo = Instant.now().minus(Duration.ofMinutes(31))
        val req = request(state = AvailabilityState.AVAILABLE, lastNotified = thirtyOneMinutesAgo)
        `when`(searchRequestRepository.save(any(SearchRequest::class.java))).thenAnswer { it.arguments[0] }
        `when`(outboxRepository.save(any(NotificationOutbox::class.java))).thenAnswer { it.arguments[0] }

        service.processUserResults(listOf(result(req, true)), user)

        val captor = ArgumentCaptor.forClass(NotificationOutbox::class.java)
        verify(outboxRepository).save(captor.capture())
        assertEquals(OutboxType.REMINDER, captor.value.type)
    }

    @Test
    fun `AVAILABLE to AVAILABLE with user_paused inserts no outbox row`() {
        val req = request(state = AvailabilityState.AVAILABLE, paused = true)
        `when`(searchRequestRepository.save(any(SearchRequest::class.java))).thenAnswer { it.arguments[0] }

        service.processUserResults(listOf(result(req, true)), user)

        org.mockito.Mockito
            .verify(outboxRepository, org.mockito.Mockito.never())
            .save(any(NotificationOutbox::class.java))
    }

    @Test
    fun `null to UNAVAILABLE inserts no outbox row`() {
        val req = request(state = null)
        `when`(searchRequestRepository.save(any(SearchRequest::class.java))).thenAnswer { it.arguments[0] }

        service.processUserResults(listOf(result(req, false)), user)

        org.mockito.Mockito
            .verify(outboxRepository, org.mockito.Mockito.never())
            .save(any(NotificationOutbox::class.java))
    }

    @Test
    fun `quiet hours defers send_after to 6am local`() {
        val threeAm = Instant.parse("2024-06-15T03:00:00Z")
        val sendAfter = service.computeSendAfter(threeAm, "UTC")
        assertEquals(Instant.parse("2024-06-15T06:00:00Z"), sendAfter)
    }

    @Test
    fun `outside quiet hours send_after equals now`() {
        val tenAm = Instant.parse("2024-06-15T10:00:00Z")
        val sendAfter = service.computeSendAfter(tenAm, "UTC")
        assertEquals(tenAm, sendAfter)
    }
}
