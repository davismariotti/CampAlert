package com.davismariotti.campalert.service.scheduling

import com.davismariotti.campalert.recreation.Campground
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.availability.AvailabilityResult
import com.davismariotti.campalert.service.availability.RecreationService
import com.davismariotti.campalert.service.state.AvailabilityStateService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@Service
class AvailabilityChecker(
    private val searchRequestRepository: SearchRequestRepository,
    private val userRepository: UserRepository,
    private val recreationService: RecreationService,
    private val availabilityStateService: AvailabilityStateService,
    private val eventPublisher: ApplicationEventPublisher,
    @param:Value("\${campfinder.checker.thread-pool-size:20}")
    private val threadPoolSize: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun processSearchRequests() {
        val allRequests = searchRequestRepository.findByCompletedFalse()

        // Auto-complete past-date requests first, using campground local midnight
        allRequests.filter { it.startDay < today(it.campgroundTimezone) }.forEach { req ->
            searchRequestRepository.save(req.copy(completed = true))
        }

        val active = allRequests.filter { req ->
            req.startDay >= today(req.campgroundTimezone) && req.pauseReason == null && req.userId != null
        }
        if (active.isEmpty()) return

        val userMap = userRepository.findAllById(active.mapNotNull { it.userId }.toSet())
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

        val executor = Executors.newFixedThreadPool(threadPoolSize)
        val futures = valid.map { request ->
            CompletableFuture.runAsync(
                {
                    val uid = request.userId!!
                    try {
                        val user = userMap[uid]!!
                        val result = recreationService.checkAvailability(request, user, cache)
                        userResults[uid]!!.add(result)
                    } catch (e: Exception) {
                        log.error("Error processing request=${request.id}", e)
                    } finally {
                        val remaining = userCountdowns[uid]!!.decrementAndGet()
                        if (remaining == 0) {
                            val user = userMap[uid]
                            if (user != null) {
                                val results = userResults[uid] ?: emptyList()
                                try {
                                    availabilityStateService.processUserResults(results, user)
                                } catch (e: Exception) {
                                    log.error("Error persisting state for user=$uid", e)
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
        executor.shutdown()
    }

    private fun today(campgroundTimezone: String?): LocalDate =
        LocalDate.now(campgroundTimezone?.let { ZoneId.of(it) } ?: ZoneOffset.UTC)
}
