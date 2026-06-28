package com.davismariotti.campalert.service.notification

import com.davismariotti.campalert.model.AvailabilityState
import com.davismariotti.campalert.model.NotificationOutbox
import com.davismariotti.campalert.model.OutboxType
import com.davismariotti.campalert.model.PhoneNumber
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.repository.NotificationOutboxRepository
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate

class CampsiteAlertDispatcherIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var dispatcher: CampsiteAlertDispatcher

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var phoneNumberRepository: PhoneNumberRepository

    @Autowired
    private lateinit var searchRequestRepository: SearchRequestRepository

    @Autowired
    private lateinit var outboxRepository: NotificationOutboxRepository

    private var userId: Long = 0L

    @BeforeEach
    fun seedUser() {
        val user = userRepository.save(User(email = "dispatcher@test.com", passwordHash = "hash"))
        userId = user.id!!
    }

    private fun seedVerifiedPhone(): PhoneNumber =
        phoneNumberRepository.save(
            PhoneNumber(
                userId = userId,
                phone = "+12125550199",
                status = PhoneNumberStatus.VERIFIED,
                smsConsentAt = Instant.now(),
            )
        )

    private fun seedRequest(availabilityState: AvailabilityState = AvailabilityState.AVAILABLE): SearchRequest =
        searchRequestRepository.save(
            SearchRequest(
                userId = userId,
                startDay = LocalDate.of(2027, 8, 1),
                nights = 2,
                groupSize = 4,
                campsiteId = 42,
                name = "Pine Camp",
                campgroundName = "Pine Valley",
                completed = false,
                lastAvailabilityState = availabilityState,
            )
        )

    private fun seedClaimableRow(
        request: SearchRequest,
        type: OutboxType = OutboxType.AVAILABLE,
        sendAfter: Instant = Instant.now().minusSeconds(60),
    ): NotificationOutbox =
        outboxRepository.save(
            NotificationOutbox(
                userId = userId,
                requestId = request.id!!,
                type = type,
                sendAfter = sendAfter,
            )
        )

    private fun runSafetyNet() {
        CampsiteAlertDispatcher::class.java
            .getDeclaredMethod("safetyNet")
            .also { it.isAccessible = true }
            .invoke(dispatcher)
    }

    private fun reload(row: NotificationOutbox): NotificationOutbox = outboxRepository.findById(row.id!!).orElseThrow()

    // --- this is the key regression test: proves processUser is transactional ---

    @Test
    fun `successful dispatch sets sentAt on outbox row`() {
        seedVerifiedPhone()
        val request = seedRequest()
        val row = seedClaimableRow(request)

        runSafetyNet()

        assertThat(reload(row).sentAt).isNotNull()
        assertThat(reload(row).missedAt).isNull()
    }

    // --- remaining dispatcher lifecycle tests ---

    @Test
    fun `no verified phone marks outbox row as missedAt`() {
        // no phone seeded
        val request = seedRequest()
        val row = seedClaimableRow(request)

        runSafetyNet()

        assertThat(reload(row).missedAt).isNotNull()
        assertThat(reload(row).sentAt).isNull()
        verify(smsSender, never()).send(anyKt(), anyKt())
    }

    @Test
    fun `send failure clears claimedAt and increments attemptCount`() {
        seedVerifiedPhone()
        val request = seedRequest()
        val row = seedClaimableRow(request)
        doThrow(RuntimeException("SMS down")).`when`(smsSender).send(anyKt(), anyKt())

        runSafetyNet()

        val reloaded = reload(row)
        assertThat(reloaded.claimedAt).isNull()
        assertThat(reloaded.attemptCount).isEqualTo(1)
        assertThat(reloaded.sentAt).isNull()
    }

    @Test
    fun `duplicate UNAVAILABLE outbox rows for same request sends only latest`() {
        seedVerifiedPhone()
        val request = seedRequest(availabilityState = AvailabilityState.UNAVAILABLE)
        val now = Instant.now()
        val older = seedClaimableRow(request, type = OutboxType.UNAVAILABLE, sendAfter = now.minusSeconds(120))
        val latest = seedClaimableRow(request, type = OutboxType.UNAVAILABLE, sendAfter = now.minusSeconds(10))

        runSafetyNet()

        assertThat(reload(older).missedAt).isNotNull()
        assertThat(reload(older).sentAt).isNull()
        assertThat(reload(latest).sentAt).isNotNull()
        assertThat(reload(latest).missedAt).isNull()
        verify(smsSender).send(anyKt(), anyKt())
    }

    @Test
    fun `stale availability state marks missedAt without sending`() {
        seedVerifiedPhone()
        val request = seedRequest(availabilityState = AvailabilityState.UNAVAILABLE)
        val row = seedClaimableRow(request, type = OutboxType.AVAILABLE)

        runSafetyNet()

        assertThat(reload(row).missedAt).isNotNull()
        assertThat(reload(row).sentAt).isNull()
        verify(smsSender, never()).send(anyKt(), anyKt())
    }
}
