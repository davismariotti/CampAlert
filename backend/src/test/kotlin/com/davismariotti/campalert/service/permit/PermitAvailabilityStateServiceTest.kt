package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.model.AvailabilityState
import com.davismariotti.campalert.model.NotificationOutbox
import com.davismariotti.campalert.model.OutboxType
import com.davismariotti.campalert.model.PermitSearchRequest
import com.davismariotti.campalert.model.PermitSearchRequestState
import com.davismariotti.campalert.model.RequestType
import com.davismariotti.campalert.model.SearchType
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.repository.NotificationOutboxRepository
import com.davismariotti.campalert.repository.PermitSearchRequestRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.service.state.AvailabilityStateService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.LocalDate

class PermitAvailabilityStateServiceTest {
    private val permitSearchRequestRepository = mock(PermitSearchRequestRepository::class.java)
    private val outboxRepository = mock(NotificationOutboxRepository::class.java)

    // Real instance (not mocked) — computeSendAfter is a pure quiet-hours calculation, no need to
    // fight Kotlin's final-by-default classes just to stub it.
    private val availabilityStateService = AvailabilityStateService(
        mock(SearchRequestRepository::class.java),
        mock(NotificationOutboxRepository::class.java),
    )

    private val service = PermitAvailabilityStateService(permitSearchRequestRepository, outboxRepository, availabilityStateService)

    private val user = User(id = 1L, email = "a@b.com", passwordHash = "x", timezone = "UTC")

    private fun zoneRequest(state: AvailabilityState? = null): PermitSearchRequest {
        val req = PermitSearchRequest(id = 10L, permitId = "233261", permitName = "Desolation", groupSize = 2, name = "Test", userId = 1L, searchType = SearchType.ZONE)
        val st = PermitSearchRequestState()
        st.permitSearchRequest = req
        st.permitSearchRequestId = 10L
        st.lastAvailabilityState = state
        req.state = st
        return req
    }

    private fun itineraryRequest(state: AvailabilityState? = null): PermitSearchRequest {
        val req = PermitSearchRequest(id = 11L, permitId = "4675323", permitName = "Yellowstone", groupSize = 2, name = "Test", userId = 1L, searchType = SearchType.ITINERARY)
        val st = PermitSearchRequestState()
        st.permitSearchRequest = req
        st.permitSearchRequestId = 11L
        st.lastAvailabilityState = state
        req.state = st
        return req
    }

    private fun stubSaves() {
        `when`(permitSearchRequestRepository.save(any(PermitSearchRequest::class.java))).thenAnswer { it.arguments[0] }
        `when`(outboxRepository.save(any(NotificationOutbox::class.java))).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `available zone result populates matched division and date`() {
        stubSaves()
        val req = zoneRequest(state = null)

        service.processUserResults(
            listOf(PermitAvailabilityResult(req, hasAvailability = true, matchedDivisionId = "343", matchedDate = LocalDate.of(2026, 7, 12))),
            user,
        )

        assertEquals("343", req.state.matchedDivisionId)
        assertEquals(LocalDate.of(2026, 7, 12), req.state.matchedDate)
    }

    @Test
    fun `becoming unavailable clears matched division and date`() {
        stubSaves()
        val req = zoneRequest(state = AvailabilityState.AVAILABLE)
        req.state.matchedDivisionId = "343"
        req.state.matchedDate = LocalDate.of(2026, 7, 12)

        service.processUserResults(listOf(PermitAvailabilityResult(req, hasAvailability = false)), user)

        assertNull(req.state.matchedDivisionId)
        assertNull(req.state.matchedDate)
    }

    @Test
    fun `unavailable itinerary result populates blocking division and date`() {
        stubSaves()
        val req = itineraryRequest(state = null)

        service.processUserResults(
            listOf(PermitAvailabilityResult(req, hasAvailability = false, blockingDivisionId = "4675323002", blockingDate = LocalDate.of(2026, 7, 13))),
            user,
        )

        assertEquals("4675323002", req.state.blockingDivisionId)
        assertEquals(LocalDate.of(2026, 7, 13), req.state.blockingDate)
    }

    @Test
    fun `becoming available clears blocking division and date`() {
        stubSaves()
        val req = itineraryRequest(state = AvailabilityState.UNAVAILABLE)
        req.state.blockingDivisionId = "4675323002"
        req.state.blockingDate = LocalDate.of(2026, 7, 13)

        service.processUserResults(listOf(PermitAvailabilityResult(req, hasAvailability = true)), user)

        assertNull(req.state.blockingDivisionId)
        assertNull(req.state.blockingDate)
    }

    @Test
    fun `null to AVAILABLE inserts AVAILABLE outbox row with PERMIT request type`() {
        stubSaves()
        val req = zoneRequest(state = null)

        service.processUserResults(
            listOf(PermitAvailabilityResult(req, hasAvailability = true, matchedDivisionId = "343", matchedDate = LocalDate.now())),
            user,
        )

        val captor = ArgumentCaptor.forClass(NotificationOutbox::class.java)
        verify(outboxRepository).save(captor.capture())
        assertEquals(OutboxType.AVAILABLE, captor.value.type)
        assertEquals(RequestType.PERMIT, captor.value.requestType)
        assertEquals(10L, captor.value.requestId)
    }

    @Test
    fun `AVAILABLE to UNAVAILABLE inserts UNAVAILABLE outbox row`() {
        stubSaves()
        val req = zoneRequest(state = AvailabilityState.AVAILABLE)

        service.processUserResults(listOf(PermitAvailabilityResult(req, hasAvailability = false)), user)

        val captor = ArgumentCaptor.forClass(NotificationOutbox::class.java)
        verify(outboxRepository).save(captor.capture())
        assertEquals(OutboxType.UNAVAILABLE, captor.value.type)
    }
}
