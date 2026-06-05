package com.davismariotti.campalert.service.notification

import com.davismariotti.campalert.model.NotificationOutbox
import com.davismariotti.campalert.model.PhoneNumber
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.repository.NotificationOutboxRepository
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.service.sms.PendingNotification
import com.davismariotti.campalert.service.sms.SmsConversationService
import com.davismariotti.campalert.service.sms.SmsNotificationService
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Instant
import java.time.LocalDate
import java.util.Optional

class NotificationProcessorTest {
    // Kotlin + Mockito: any() / capture() returns null but Kotlin adds a null check at the call
    // site when passing to a non-nullable param. Casting as T suppresses that check without
    // adding mockito-kotlin.
    @Suppress("UNCHECKED_CAST")
    private fun <T> anyK(): T = Mockito.any<T>() as T

    @Suppress("UNCHECKED_CAST")
    private fun <T> captureK(captor: ArgumentCaptor<T>): T {
        captor.capture()
        return null as T
    }

    private val outboxRepo = mock(NotificationOutboxRepository::class.java)
    private val searchRequestRepo = mock(SearchRequestRepository::class.java)
    private val phoneRepo = mock(PhoneNumberRepository::class.java)
    private val smsSvc = mock(SmsNotificationService::class.java)
    private val conversationSvc = mock(SmsConversationService::class.java)

    private val processor = NotificationProcessor(outboxRepo, searchRequestRepo, phoneRepo, smsSvc, conversationSvc)

    private val phone = PhoneNumber(
        id = 1L,
        userId = 42L,
        phone = "+15005550006",
        status = PhoneNumberStatus.VERIFIED,
        smsConsentAt = Instant.now(),
    )

    private fun request(id: Int, state: String = "AVAILABLE") =
        SearchRequest(
            id = id, startDay = LocalDate.now().plusDays(5), nights = 2, groupSize = 2,
            campsiteId = 99, name = "Camp", completed = false, campgroundName = "Camp Ground",
            lastAvailabilityState = state,
        )

    private fun outboxRow(id: Long, requestId: Int, type: String) =
        NotificationOutbox(
            id = id,
            userId = 42L,
            requestId = requestId,
            type = type,
            sendAfter = Instant.now().minusSeconds(10),
        )

    @Test
    fun `0 rows claimed — skips processing`() {
        val rows = listOf(outboxRow(1L, 10, "AVAILABLE"))
        `when`(outboxRepo.claimRows(anyK(), anyK())).thenReturn(0)

        processor.processUser(42L, rows, Instant.now())

        verify(smsSvc, never()).notifyAggregated(anyK(), anyK())
    }

    @Test
    fun `stale AVAILABLE row marked missed_at`() {
        val req = request(10, state = "UNAVAILABLE")
        val row = outboxRow(1L, 10, "AVAILABLE")
        `when`(outboxRepo.claimRows(anyK(), anyK())).thenReturn(1)
        `when`(phoneRepo.findByUserIdAndStatus(anyLong(), anyK())).thenReturn(listOf(phone))
        `when`(searchRequestRepo.findById(10)).thenReturn(Optional.of(req))
        `when`(outboxRepo.save(anyK())).thenAnswer { it.arguments[0] }

        processor.processUser(42L, listOf(row), Instant.now())

        verify(smsSvc, never()).notifyAggregated(anyK(), anyK())
        val captor = ArgumentCaptor.forClass(NotificationOutbox::class.java)
        verify(outboxRepo).save(captureK(captor))
        assertNull(captor.value?.sentAt, "sentAt should not be set on stale row")
        assert(captor.value?.missedAt != null) { "missedAt should be set on stale row" }
    }

    @Test
    fun `multiple AVAILABLE rows sent as single aggregated SMS`() {
        val req1 = request(10)
        val req2 = request(11)
        val row1 = outboxRow(1L, 10, "AVAILABLE")
        val row2 = outboxRow(2L, 11, "AVAILABLE")
        `when`(outboxRepo.claimRows(anyK(), anyK())).thenReturn(2)
        `when`(phoneRepo.findByUserIdAndStatus(anyLong(), anyK())).thenReturn(listOf(phone))
        `when`(searchRequestRepo.findById(10)).thenReturn(Optional.of(req1))
        `when`(searchRequestRepo.findById(11)).thenReturn(Optional.of(req2))
        `when`(outboxRepo.findById(1L)).thenReturn(Optional.of(row1))
        `when`(outboxRepo.findById(2L)).thenReturn(Optional.of(row2))
        `when`(outboxRepo.save(anyK())).thenAnswer { it.arguments[0] }
        `when`(searchRequestRepo.save(anyK())).thenAnswer { it.arguments[0] }

        processor.processUser(42L, listOf(row1, row2), Instant.now())

        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<PendingNotification>>
        verify(smsSvc).notifyAggregated(anyK(), captureK(captor))
        assert(captor.value.size == 2) { "Should aggregate 2 notifications into one SMS call" }
    }

    @Test
    fun `Twilio failure clears claimed_at and increments attempt_count`() {
        val req = request(10)
        val row = outboxRow(1L, 10, "AVAILABLE")
        `when`(outboxRepo.claimRows(anyK(), anyK())).thenReturn(1)
        `when`(phoneRepo.findByUserIdAndStatus(anyLong(), anyK())).thenReturn(listOf(phone))
        `when`(searchRequestRepo.findById(10)).thenReturn(Optional.of(req))
        `when`(smsSvc.notifyAggregated(anyK(), anyK())).thenThrow(RuntimeException("Twilio down"))
        `when`(outboxRepo.findById(1L)).thenReturn(Optional.of(row))
        `when`(outboxRepo.save(anyK())).thenAnswer { it.arguments[0] }

        processor.processUser(42L, listOf(row), Instant.now())

        val captor = ArgumentCaptor.forClass(NotificationOutbox::class.java)
        verify(outboxRepo).save(captureK(captor))
        assertNull(captor.value?.claimedAt, "claimedAt should be cleared on failure")
        assert(captor.value?.attemptCount == 1) { "attempt_count should be incremented" }
    }
}
