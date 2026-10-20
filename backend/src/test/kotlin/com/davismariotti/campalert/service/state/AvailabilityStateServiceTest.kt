package com.davismariotti.campalert.service.state

import com.davismariotti.campalert.model.AvailabilityState
import com.davismariotti.campalert.model.NotificationOutbox
import com.davismariotti.campalert.model.OutboxType
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.SearchRequestState
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.repository.NotificationOutboxRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.service.availability.AvailabilityResult
import com.davismariotti.campalert.service.availability.GlobalEntryMatch
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
import java.time.LocalTime

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

    private fun result(
        request: SearchRequest,
        available: Boolean,
        matchedStartDay: LocalDate? = null,
        matchedEndDay: LocalDate? = null
    ) = AvailabilityResult(
        searchRequest = request,
        hasAvailableSites = available,
        availableSiteCount = if (available) 1 else 0,
        matchedStartDay = matchedStartDay,
        matchedEndDay = matchedEndDay,
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
    fun `available result persists matched dates onto state`() {
        val req = request(state = AvailabilityState.UNAVAILABLE)
        val matchedStart = req.startDay.plusDays(1)
        val matchedEnd = req.startDay.plusDays(3)
        `when`(searchRequestRepository.save(any(SearchRequest::class.java))).thenAnswer { it.arguments[0] }
        `when`(outboxRepository.save(any(NotificationOutbox::class.java))).thenAnswer { it.arguments[0] }

        service.processUserResults(listOf(result(req, true, matchedStart, matchedEnd)), user)

        assertEquals(matchedStart, req.state.matchedStartDay)
        assertEquals(matchedEnd, req.state.matchedEndDay)
    }

    @Test
    fun `unavailable result clears previously matched dates`() {
        val req = request(state = AvailabilityState.AVAILABLE)
        req.state.matchedStartDay = req.startDay.plusDays(1)
        req.state.matchedEndDay = req.startDay.plusDays(3)
        `when`(searchRequestRepository.save(any(SearchRequest::class.java))).thenAnswer { it.arguments[0] }
        `when`(outboxRepository.save(any(NotificationOutbox::class.java))).thenAnswer { it.arguments[0] }

        service.processUserResults(listOf(result(req, false)), user)

        assertNull(req.state.matchedStartDay)
        assertNull(req.state.matchedEndDay)
    }

    @Test
    fun `matched dates changing while continuously available does not by itself add a second outbox row`() {
        val thirtyMinutesAgo = Instant.now().minus(Duration.ofMinutes(10))
        val req = request(state = AvailabilityState.AVAILABLE, lastNotified = thirtyMinutesAgo)
        req.state.matchedStartDay = req.startDay.plusDays(1)
        req.state.matchedEndDay = req.startDay.plusDays(3)
        `when`(searchRequestRepository.save(any(SearchRequest::class.java))).thenAnswer { it.arguments[0] }

        // Different matched dates than what's already on state, but still available and within the
        // reminder cooldown — no REMINDER should fire, and matched dates should still update.
        service.processUserResults(listOf(result(req, true, req.startDay.plusDays(4), req.startDay.plusDays(6))), user)

        org.mockito.Mockito
            .verify(outboxRepository, org.mockito.Mockito.never())
            .save(any(NotificationOutbox::class.java))
        assertEquals(req.startDay.plusDays(4), req.state.matchedStartDay)
        assertEquals(req.startDay.plusDays(6), req.state.matchedEndDay)
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

    private fun globalEntryRequest(state: AvailabilityState? = null, lastNotified: Instant? = null): SearchRequest {
        val base = request(state = state, lastNotified = lastNotified)
        val req = base.copy(provider = Provider.GLOBAL_ENTRY, nights = 1, groupSize = 1)
        req.state = base.state
        base.state.searchRequest = req
        return req
    }

    private fun globalEntryResult(
        req: SearchRequest,
        available: Boolean,
        time: LocalTime = LocalTime.of(11, 40),
        count: Int = 1
    ) = AvailabilityResult(
        searchRequest = req,
        hasAvailableSites = available,
        availableSiteCount = if (available) count else 0,
        matchedStartDay = if (available) req.startDay else null,
        matchedEndDay = if (available) req.startDay else null,
        globalEntryMatch = if (available) GlobalEntryMatch(time, count) else null,
    )

    @Test
    fun `global entry available result persists the earliest slot time and count`() {
        val req = globalEntryRequest(state = AvailabilityState.UNAVAILABLE)
        `when`(searchRequestRepository.save(any(SearchRequest::class.java))).thenAnswer { it.arguments[0] }
        `when`(outboxRepository.save(any(NotificationOutbox::class.java))).thenAnswer { it.arguments[0] }

        service.processUserResults(listOf(globalEntryResult(req, true, LocalTime.of(11, 40), 3)), user)

        assertEquals(LocalTime.of(11, 40), req.globalEntryState!!.matchedStartTime)
        assertEquals(3, req.globalEntryState!!.matchedSlotCount)
        assertEquals(req.startDay, req.state.matchedStartDay)
    }

    @Test
    fun `global entry unavailable result clears the slot time and count`() {
        val req = globalEntryRequest(state = AvailabilityState.AVAILABLE)
        `when`(searchRequestRepository.save(any(SearchRequest::class.java))).thenAnswer { it.arguments[0] }
        `when`(outboxRepository.save(any(NotificationOutbox::class.java))).thenAnswer { it.arguments[0] }
        service.processUserResults(listOf(globalEntryResult(req, true, LocalTime.of(11, 40), 2)), user)

        service.processUserResults(listOf(globalEntryResult(req, false)), user)

        assertNull(req.globalEntryState!!.matchedStartTime)
        assertNull(req.globalEntryState!!.matchedSlotCount)
    }

    @Test
    fun `global entry slot count changing while continuously available updates the count without a new outbox row`() {
        val req = globalEntryRequest(state = AvailabilityState.AVAILABLE, lastNotified = Instant.now().minus(Duration.ofMinutes(10)))
        `when`(searchRequestRepository.save(any(SearchRequest::class.java))).thenAnswer { it.arguments[0] }
        service.processUserResults(listOf(globalEntryResult(req, true, LocalTime.of(11, 40), 1)), user)

        service.processUserResults(listOf(globalEntryResult(req, true, LocalTime.of(11, 40), 2)), user)

        assertEquals(2, req.globalEntryState!!.matchedSlotCount)
        org.mockito.Mockito
            .verify(outboxRepository, org.mockito.Mockito.never())
            .save(any(NotificationOutbox::class.java))
    }

    @Test
    fun `a non global entry request never gets a global entry state row`() {
        val req = request(state = AvailabilityState.UNAVAILABLE)
        `when`(searchRequestRepository.save(any(SearchRequest::class.java))).thenAnswer { it.arguments[0] }
        `when`(outboxRepository.save(any(NotificationOutbox::class.java))).thenAnswer { it.arguments[0] }

        service.processUserResults(listOf(globalEntryResult(req, true)), user)

        assertNull(req.globalEntryState)
    }
}
