package com.davismariotti.campalert.service.scheduling

import com.davismariotti.campalert.recreation.Campground
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.availability.AvailabilityResult
import com.davismariotti.campalert.service.availability.RecreationService
import com.davismariotti.campalert.service.state.AvailabilityStateService
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

@Service
class AvailabilityChecker(
    private val searchRequestRepository: SearchRequestRepository,
    private val userRepository: UserRepository,
    private val recreationService: RecreationService,
    private val availabilityStateService: AvailabilityStateService,
    private val eventPublisher: ApplicationEventPublisher,
    @Qualifier("availabilityCheckerExecutor") private val executor: Executor,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @SchedulerLock(name = "availabilityChecker", lockAtMostFor = "PT90S", lockAtLeastFor = "PT30S")
    fun processSearchRequests() {
        val allRequests = searchRequestRepository.findAllIncomplete()

        // Auto-complete past-date requests first, using campground local midnight
        val toComplete = allRequests.filter { it.startDay < today(it.campgroundTimezone) }
        toComplete.forEach { req ->
            req.state.completed = true
            searchRequestRepository.save(req)
        }

        val active = allRequests.filter { req ->
            req.startDay >= today(req.campgroundTimezone) && req.state.pauseReason == null && req.userId != null
        }
        log.info("Availability check started active={} autoCompleted={}", active.size, toComplete.size)
        if (active.isEmpty()) return

        val userMap = userRepository
            .findAllById(active.mapNotNull { it.userId }.toSet())
            .associateBy { it.id!! }
        val valid = active.filter { userMap.containsKey(it.userId) }
        if (valid.isEmpty()) return

        // Per-user counters and result buckets
        val userCounts = valid.groupBy { it.userId!! }.mapValues { it.value.size }
        val userCountdowns = userCounts.mapValues { AtomicInteger(it.value) }.toMutableMap()
        val userResults = ConcurrentHashMap<Long, MutableList<AvailabilityResult>>()
        userCounts.keys.forEach { uid -> userResults[uid] = mutableListOf() }

        // Tick-scoped dedup cache: (campsiteId, yearMonth) → Future<Campground>
        val cache = ConcurrentHashMap<Pair<Int, YearMonth>, CompletableFuture<Campground>>()

        val futures = valid.map { request ->
            CompletableFuture.runAsync(
                {
                    val uid = request.userId!!
                    try {
                        val user = userMap[uid]!!
                        val result = recreationService.checkAvailability(request, user, cache)
                        userResults[uid]!!.add(result)
                    } catch (e: Exception) {
                        log.error("Error processing requestId={}", request.id, e)
                    } finally {
                        val remaining = userCountdowns[uid]!!.decrementAndGet()
                        if (remaining == 0) {
                            val user = userMap[uid]
                            if (user != null) {
                                val results = userResults[uid] ?: emptyList()
                                try {
                                    availabilityStateService.processUserResults(results, user)
                                } catch (e: Exception) {
                                    log.error("Error persisting state for userId={}", uid, e)
                                }
                            }
                            eventPublisher.publishEvent(UserAvailabilityProcessedEvent(uid))
                        }
                    }
                },
                executor,
            )
        }

        CompletableFuture.allOf(*futures.toTypedArray()).join()
    }

    private fun today(campgroundTimezone: String?): LocalDate = LocalDate.now(campgroundTimezone?.let { ZoneId.of(it) } ?: ZoneOffset.UTC)
}
