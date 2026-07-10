package com.davismariotti.campalert.service.notification

import com.davismariotti.campalert.model.AvailabilityState
import com.davismariotti.campalert.model.NotificationOutbox
import com.davismariotti.campalert.model.OutboxType
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.SearchRequestState
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.repository.NotificationOutboxRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.sms.SmsConversationService
import com.davismariotti.notifications.Channel
import com.davismariotti.notifications.DispatchResult
import com.davismariotti.notifications.PushoverSender
import com.davismariotti.notifications.Recipient
import com.davismariotti.notifications.SendResult
import com.davismariotti.notifications.SimpleRecipient
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Instant
import java.time.LocalDate
import java.util.Optional

class CampsiteAlertDispatcherTest {
    @Suppress("UNCHECKED_CAST")
    private fun <T> anyK(): T = Mockito.any<T>() as T

    @Suppress("UNCHECKED_CAST")
    private fun <T> captureK(captor: ArgumentCaptor<T>): T {
        captor.capture()
        return null as T
    }

    private val notificationService = mock(NotificationService::class.java)
    private val recipientResolver = mock(RecipientResolver::class.java)
    private val pushoverSender = mock(PushoverSender::class.java)
    private val outboxRepo = mock(NotificationOutboxRepository::class.java)
    private val searchRequestRepo = mock(SearchRequestRepository::class.java)
    private val permitSearchRequestRepo = mock(com.davismariotti.campalert.repository.PermitSearchRequestRepository::class.java)
    private val userRepo = mock(UserRepository::class.java)
    private val conversationSvc = mock(SmsConversationService::class.java)

    private val processor = CampsiteAlertOutboxProcessor(
        notificationService = notificationService,
        recipientResolver = recipientResolver,
        pushoverSender = pushoverSender,
        notificationOutboxRepository = outboxRepo,
        searchRequestRepository = searchRequestRepo,
        permitSearchRequestRepository = permitSearchRequestRepo,
        userRepository = userRepo,
        smsConversationService = conversationSvc,
    )

    private val smsUser = User(id = 42L, email = "user@example.com", passwordHash = "hash")
    private val recipientWithPhone: Recipient = SimpleRecipient(email = smsUser.email, phone = "+15005550006")

    private fun dispatchResult(success: Boolean): DispatchResult =
        DispatchResult(
            mapOf(
                Channel.SMS to
                    if (success) {
                        SendResult.success()
                    } else {
                        SendResult.failure(RuntimeException("Send failed"), retryable = true)
                    },
            ),
        )

    @BeforeEach
    fun setUp() {
        `when`(userRepo.findById(42L)).thenReturn(Optional.of(smsUser))
        `when`(recipientResolver.resolve(smsUser)).thenReturn(recipientWithPhone)
        `when`(notificationService.send(anyK(), anyK())).thenReturn(dispatchResult(success = true))
    }

    private fun request(id: Long, state: AvailabilityState = AvailabilityState.AVAILABLE): SearchRequest {
        val req = SearchRequest(
            id = id,
            startDay = LocalDate.now().plusDays(5),
            nights = 2,
            groupSize = 2,
            campsiteId = 99,
            name = "Camp",
            campgroundName = "Camp Ground",
        )
        val st = SearchRequestState()
        st.searchRequest = req
        st.searchRequestId = id
        st.lastAvailabilityState = state
        req.state = st
        return req
    }

    private fun outboxRow(
        id: Long,
        requestId: Long,
        type: OutboxType,
        sendAfter: Instant = Instant.now().minusSeconds(10),
    ) = NotificationOutbox(
        id = id,
        userId = 42L,
        requestId = requestId,
        type = type,
        sendAfter = sendAfter,
    )

    @Test
    fun `0 rows claimed skips processing`() {
        val rows = listOf(outboxRow(1L, 10L, OutboxType.AVAILABLE))
        `when`(outboxRepo.claimRows(anyK(), anyK())).thenReturn(0)

        processor.processUser(42L, rows, Instant.now())

        verify(notificationService, never()).send(anyK(), anyK())
    }

    @Test
    fun `stale AVAILABLE row is marked missedAt`() {
        val req = request(10L, state = AvailabilityState.UNAVAILABLE)
        val row = outboxRow(1L, 10L, OutboxType.AVAILABLE)
        `when`(outboxRepo.claimRows(anyK(), anyK())).thenReturn(1)
        `when`(searchRequestRepo.findAllById(anyK())).thenReturn(listOf(req))
        `when`(outboxRepo.save(anyK())).thenAnswer { it.arguments[0] }

        processor.processUser(42L, listOf(row), Instant.now())

        verify(notificationService, never()).send(anyK(), anyK())
        val captor = ArgumentCaptor.forClass(NotificationOutbox::class.java)
        verify(outboxRepo).save(captureK(captor))
        assertNull(captor.value?.sentAt, "sentAt should not be set on stale row")
        assert(captor.value?.missedAt != null) { "missedAt should be set on stale row" }
    }

    @Test
    fun `multiple AVAILABLE rows sent as single aggregated notification`() {
        val req1 = request(10L)
        val req2 = request(11L)
        val row1 = outboxRow(1L, 10L, OutboxType.AVAILABLE)
        val row2 = outboxRow(2L, 11L, OutboxType.AVAILABLE)
        `when`(outboxRepo.claimRows(anyK(), anyK())).thenReturn(2)
        `when`(searchRequestRepo.findAllById(anyK())).thenReturn(listOf(req1, req2))
        `when`(outboxRepo.save(anyK())).thenAnswer { it.arguments[0] }
        `when`(searchRequestRepo.save(anyK())).thenAnswer { it.arguments[0] }

        processor.processUser(42L, listOf(row1, row2), Instant.now())

        verify(notificationService).send(anyK(), anyK())
    }

    @Test
    fun `successful send marks outbox rows as sentAt`() {
        val req = request(10L)
        val row = outboxRow(1L, 10L, OutboxType.AVAILABLE)
        `when`(outboxRepo.claimRows(anyK(), anyK())).thenReturn(1)
        `when`(searchRequestRepo.findAllById(anyK())).thenReturn(listOf(req))
        `when`(outboxRepo.save(anyK())).thenAnswer { it.arguments[0] }
        `when`(searchRequestRepo.save(anyK())).thenAnswer { it.arguments[0] }

        processor.processUser(42L, listOf(row), Instant.now())

        val captor = ArgumentCaptor.forClass(NotificationOutbox::class.java)
        verify(outboxRepo).save(captureK(captor))
        assert(captor.value?.sentAt != null) { "sentAt should be set on successful send" }
    }

    @Test
    fun `send failure clears claimedAt and increments attemptCount`() {
        val req = request(10L)
        val row = outboxRow(1L, 10L, OutboxType.AVAILABLE)
        `when`(outboxRepo.claimRows(anyK(), anyK())).thenReturn(1)
        `when`(searchRequestRepo.findAllById(anyK())).thenReturn(listOf(req))
        `when`(notificationService.send(anyK(), anyK())).thenReturn(dispatchResult(success = false))
        `when`(outboxRepo.save(anyK())).thenAnswer { it.arguments[0] }

        processor.processUser(42L, listOf(row), Instant.now())

        val captor = ArgumentCaptor.forClass(NotificationOutbox::class.java)
        verify(outboxRepo).save(captureK(captor))
        assertNull(captor.value?.claimedAt, "claimedAt should be cleared on failure")
        assert(captor.value?.attemptCount == 1) { "attemptCount should be incremented" }
    }

    @Test
    fun `duplicate UNAVAILABLE rows for same request sends only latest and misses older`() {
        val req = request(10L, state = AvailabilityState.UNAVAILABLE)
        val now = Instant.now()
        val older = outboxRow(1L, 10L, OutboxType.UNAVAILABLE, sendAfter = now.minusSeconds(60))
        val latest = outboxRow(2L, 10L, OutboxType.UNAVAILABLE, sendAfter = now.minusSeconds(10))
        `when`(outboxRepo.claimRows(anyK(), anyK())).thenReturn(2)
        `when`(searchRequestRepo.findAllById(anyK())).thenReturn(listOf(req))
        `when`(outboxRepo.save(anyK())).thenAnswer { it.arguments[0] }

        processor.processUser(42L, listOf(older, latest), now)

        verify(notificationService).send(anyK(), anyK())
        val captor = ArgumentCaptor.forClass(NotificationOutbox::class.java)
        verify(outboxRepo, Mockito.times(2)).save(captureK(captor))
        val byId = captor.allValues.associateBy { it.id }
        assert(byId[1L]?.missedAt != null) { "older UNAVAILABLE row should be missed" }
        assertNull(byId[1L]?.sentAt)
        assert(byId[2L]?.sentAt != null) { "latest UNAVAILABLE row should be sent" }
        assertNull(byId[2L]?.missedAt)
    }

    @Test
    fun `UNAVAILABLE rows for distinct requests are each sent`() {
        val req1 = request(10L, state = AvailabilityState.UNAVAILABLE)
        val req2 = request(11L, state = AvailabilityState.UNAVAILABLE)
        val row1 = outboxRow(1L, 10L, OutboxType.UNAVAILABLE)
        val row2 = outboxRow(2L, 11L, OutboxType.UNAVAILABLE)
        `when`(outboxRepo.claimRows(anyK(), anyK())).thenReturn(2)
        `when`(searchRequestRepo.findAllById(anyK())).thenReturn(listOf(req1, req2))
        `when`(outboxRepo.save(anyK())).thenAnswer { it.arguments[0] }

        processor.processUser(42L, listOf(row1, row2), Instant.now())

        verify(notificationService).send(anyK(), anyK())
        val captor = ArgumentCaptor.forClass(NotificationOutbox::class.java)
        verify(outboxRepo, Mockito.times(2)).save(captureK(captor))
        assert(captor.allValues.all { it.sentAt != null }) { "both UNAVAILABLE rows for distinct requests should be sent" }
    }

    @Test
    fun `no verified phone marks rows missedAt`() {
        `when`(recipientResolver.resolve(smsUser)).thenReturn(SimpleRecipient(email = smsUser.email))
        val row = outboxRow(1L, 10L, OutboxType.AVAILABLE)
        `when`(outboxRepo.claimRows(anyK(), anyK())).thenReturn(1)
        `when`(outboxRepo.save(anyK())).thenAnswer { it.arguments[0] }

        processor.processUser(42L, listOf(row), Instant.now())

        verify(notificationService, never()).send(anyK(), anyK())
        val captor = ArgumentCaptor.forClass(NotificationOutbox::class.java)
        verify(outboxRepo).save(captureK(captor))
        assert(captor.value?.missedAt != null) { "missedAt should be set when no phone" }
    }
}
