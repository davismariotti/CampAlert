package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.PollTargetId
import com.davismariotti.campalert.model.PollTargetState
import com.davismariotti.campalert.model.Provider
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.SearchRequestState
import com.davismariotti.campalert.model.TargetType
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PollTargetStateDaoIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var pollTargetStateDao: PollTargetStateDao

    @Autowired
    private lateinit var pollTargetStateRepository: PollTargetStateRepository

    @Autowired
    private lateinit var searchRequestRepository: SearchRequestRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    private val campsiteId = 555555
    private lateinit var targetId: PollTargetId

    @BeforeEach
    fun seedActiveCampgroundTarget() {
        val user = userRepository.save(User(email = "poll-target@test.com", passwordHash = "hash"))
        val req = SearchRequest(
            userId = user.id,
            startDay = LocalDate.now().plusDays(10),
            nights = 2,
            groupSize = 2,
            campsiteId = campsiteId,
            name = "Test",
        )
        val state = SearchRequestState()
        state.searchRequest = req
        req.state = state
        searchRequestRepository.save(req)

        targetId = PollTargetId(TargetType.CAMPGROUND, Provider.RECREATION_GOV, campsiteId.toString())
    }

    private fun saveDueRow(nextDueAt: Instant = Instant.now().minusSeconds(1), lockedUntil: Instant? = null) {
        pollTargetStateRepository.save(
            PollTargetState(id = targetId, phaseOffsetMs = 0, nextDueAt = nextDueAt, lockedUntil = lockedUntil),
        )
    }

    @Test
    fun `claimDue returns a due, unlocked target with an active request`() {
        saveDueRow()

        val claimed = pollTargetStateDao.claimDue(Instant.now(), Instant.now().plusSeconds(60))

        assertThat(claimed).containsExactly(targetId)
    }

    @Test
    fun `claimDue does not return a row whose next_due_at is in the future`() {
        saveDueRow(nextDueAt = Instant.now().plusSeconds(60))

        val claimed = pollTargetStateDao.claimDue(Instant.now(), Instant.now().plusSeconds(60))

        assertThat(claimed).isEmpty()
    }

    @Test
    fun `claimDue does not return an already-locked row`() {
        saveDueRow(lockedUntil = Instant.now().plusSeconds(60))

        val claimed = pollTargetStateDao.claimDue(Instant.now(), Instant.now().plusSeconds(60))

        assertThat(claimed).isEmpty()
    }

    @Test
    fun `claimDue returns a row whose lock has expired`() {
        saveDueRow(lockedUntil = Instant.now().minusSeconds(5))

        val claimed = pollTargetStateDao.claimDue(Instant.now(), Instant.now().plusSeconds(60))

        assertThat(claimed).containsExactly(targetId)
    }

    @Test
    fun `claimDue does not claim a row with no active request`() {
        // Pause the only request for this campground — the EXISTS gate should now exclude it.
        val req = searchRequestRepository.findByCampsiteIdAndProviderAndCompletedFalse(campsiteId, Provider.RECREATION_GOV).single()
        req.state.pauseReason = "SYSTEM_PAUSED"
        searchRequestRepository.save(req)
        saveDueRow()

        val claimed = pollTargetStateDao.claimDue(Instant.now(), Instant.now().plusSeconds(60))

        assertThat(claimed).isEmpty()
    }

    @Test
    fun `a second claimDue call immediately after does not reclaim the same row`() {
        saveDueRow()

        val first = pollTargetStateDao.claimDue(Instant.now(), Instant.now().plusSeconds(60))
        val second = pollTargetStateDao.claimDue(Instant.now(), Instant.now().plusSeconds(60))

        assertThat(first).containsExactly(targetId)
        assertThat(second).isEmpty()
    }

    @Test
    fun `concurrent claimDue calls never double-claim the same due row`() {
        saveDueRow()

        val threadCount = 8
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val results = CopyOnWriteArrayList<List<PollTargetId>>()

        repeat(threadCount) {
            executor.submit {
                startLatch.await()
                val claimed = pollTargetStateDao.claimDue(Instant.now(), Instant.now().plusSeconds(60))
                results.add(claimed)
            }
        }
        startLatch.countDown()
        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)

        val totalClaims = results.sumOf { it.size }
        assertThat(totalClaims).isEqualTo(1)
    }

    // --- deleteStaleOrphans ---

    @Test
    fun `deleteStaleOrphans removes a stale row with no active request`() {
        val req = searchRequestRepository.findByCampsiteIdAndProviderAndCompletedFalse(campsiteId, Provider.RECREATION_GOV).single()
        req.state.completed = true
        searchRequestRepository.save(req)
        pollTargetStateRepository.save(
            PollTargetState(id = targetId, phaseOffsetMs = 0, nextDueAt = Instant.now(), lastFinishedAt = Instant.now().minusSeconds(1_000_000)),
        )

        val deleted = pollTargetStateDao.deleteStaleOrphans(Instant.now(), thresholdMs = 1000)

        assertThat(deleted).isEqualTo(1)
        assertThat(pollTargetStateRepository.findById(targetId)).isEmpty()
    }

    @Test
    fun `deleteStaleOrphans leaves a row alone if it still has an active request`() {
        // Request stays active (not completed) — the DAO's exists-check should exclude this row.
        pollTargetStateRepository.save(
            PollTargetState(id = targetId, phaseOffsetMs = 0, nextDueAt = Instant.now(), lastFinishedAt = Instant.now().minusSeconds(1_000_000)),
        )

        val deleted = pollTargetStateDao.deleteStaleOrphans(Instant.now(), thresholdMs = 1000)

        assertThat(deleted).isEqualTo(0)
        assertThat(pollTargetStateRepository.findById(targetId)).isPresent()
    }

    @Test
    fun `deleteStaleOrphans leaves a recently-touched orphan alone`() {
        val req = searchRequestRepository.findByCampsiteIdAndProviderAndCompletedFalse(campsiteId, Provider.RECREATION_GOV).single()
        req.state.completed = true
        searchRequestRepository.save(req)
        pollTargetStateRepository.save(
            // last_finished_at is recent, well inside the staleness threshold.
            PollTargetState(id = targetId, phaseOffsetMs = 0, nextDueAt = Instant.now(), lastFinishedAt = Instant.now()),
        )

        val deleted = pollTargetStateDao.deleteStaleOrphans(Instant.now(), thresholdMs = 1_000_000)

        assertThat(deleted).isEqualTo(0)
        assertThat(pollTargetStateRepository.findById(targetId)).isPresent()
    }

    @Test
    fun `deleteStaleOrphans does not remove a currently-locked row`() {
        val req = searchRequestRepository.findByCampsiteIdAndProviderAndCompletedFalse(campsiteId, Provider.RECREATION_GOV).single()
        req.state.completed = true
        searchRequestRepository.save(req)
        pollTargetStateRepository.save(
            PollTargetState(
                id = targetId,
                phaseOffsetMs = 0,
                nextDueAt = Instant.now(),
                lockedUntil = Instant.now().plusSeconds(60),
                lastFinishedAt = Instant.now().minusSeconds(1_000_000),
            ),
        )

        val deleted = pollTargetStateDao.deleteStaleOrphans(Instant.now(), thresholdMs = 1000)

        assertThat(deleted).isEqualTo(0)
        assertThat(pollTargetStateRepository.findById(targetId)).isPresent()
    }
}
