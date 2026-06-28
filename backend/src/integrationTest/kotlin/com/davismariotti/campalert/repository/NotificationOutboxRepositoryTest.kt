package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.AvailabilityState
import com.davismariotti.campalert.model.NotificationOutbox
import com.davismariotti.campalert.model.OutboxType
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class NotificationOutboxRepositoryTest : IntegrationTestBase() {
    @Autowired
    private lateinit var outboxRepository: NotificationOutboxRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var searchRequestRepository: SearchRequestRepository

    private var userId: Long = 0L
    private var requestId: Long = 0L

    private val now: Instant = Instant.now()
    private val staleThreshold: Instant = now.minus(15, ChronoUnit.MINUTES)

    @BeforeEach
    fun seedUserAndRequest() {
        val user = userRepository.save(User(email = "outbox@test.com", passwordHash = "hash"))
        userId = user.id!!
        val request = searchRequestRepository.save(
            SearchRequest(
                userId = userId,
                startDay = LocalDate.of(2027, 8, 1),
                nights = 2,
                groupSize = 4,
                campsiteId = 42,
                name = "Pine Camp",
                campgroundName = "Pine Valley",
                completed = false,
                lastAvailabilityState = AvailabilityState.AVAILABLE,
            )
        )
        requestId = request.id!!
    }

    private fun outboxRow(
        type: OutboxType = OutboxType.AVAILABLE,
        sendAfter: Instant = now.minusSeconds(60),
        sentAt: Instant? = null,
        missedAt: Instant? = null,
        claimedAt: Instant? = null,
    ) = outboxRepository.save(
        NotificationOutbox(
            userId = userId,
            requestId = requestId,
            type = type,
            sendAfter = sendAfter,
            sentAt = sentAt,
            missedAt = missedAt,
            claimedAt = claimedAt,
        )
    )

    // --- claimRows correctness (called within a transaction) ---

    @Test
    @Transactional
    fun `claimRows returns count of rows updated`() {
        val row = outboxRow()
        val claimed = outboxRepository.claimRows(listOf(row.id!!), now)
        assertThat(claimed).isEqualTo(1)
    }

    @Test
    @Transactional
    fun `claimRows is idempotent — second call on same ids returns 0`() {
        val row = outboxRow()
        outboxRepository.claimRows(listOf(row.id!!), now)
        val secondClaim = outboxRepository.claimRows(listOf(row.id!!), now)
        assertThat(secondClaim).isEqualTo(0)
    }

    // --- findClaimable query correctness ---

    @Test
    fun `findClaimable returns unclaimed row with past sendAfter`() {
        val row = outboxRow(sendAfter = now.minusSeconds(60))
        val results = outboxRepository.findClaimable(now, staleThreshold)
        assertThat(results.map { it.id }).contains(row.id)
    }

    @Test
    fun `findClaimable excludes row where sentAt is set`() {
        outboxRow(sentAt = now.minusSeconds(30))
        assertThat(outboxRepository.findClaimable(now, staleThreshold)).isEmpty()
    }

    @Test
    fun `findClaimable excludes row where missedAt is set`() {
        outboxRow(missedAt = now.minusSeconds(30))
        assertThat(outboxRepository.findClaimable(now, staleThreshold)).isEmpty()
    }

    @Test
    fun `findClaimable excludes freshly-claimed row`() {
        outboxRow(claimedAt = now.minusSeconds(30)) // after staleThreshold, so fresh
        assertThat(outboxRepository.findClaimable(now, staleThreshold)).isEmpty()
    }

    @Test
    fun `findClaimable returns stale-claimed row for reprocessing`() {
        val row = outboxRow(claimedAt = now.minus(20, ChronoUnit.MINUTES)) // older than stale threshold
        val results = outboxRepository.findClaimable(now, staleThreshold)
        assertThat(results.map { it.id }).contains(row.id)
    }

    // --- countMissedWindowsByRequestId ---

    @Test
    fun `countMissedWindowsByRequestId counts only AVAILABLE rows with missedAt`() {
        outboxRow(type = OutboxType.AVAILABLE, missedAt = now.minusSeconds(60)) // counted
        outboxRow(type = OutboxType.AVAILABLE, missedAt = now.minusSeconds(60)) // counted
        outboxRow(type = OutboxType.AVAILABLE, missedAt = null) // not counted (no missedAt)
        outboxRow(type = OutboxType.UNAVAILABLE, missedAt = now.minusSeconds(60)) // not counted (wrong type)
        assertThat(outboxRepository.countMissedWindowsByRequestId(requestId)).isEqualTo(2)
    }
}
