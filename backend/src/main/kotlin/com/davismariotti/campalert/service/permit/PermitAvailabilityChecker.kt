package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.model.PermitSearchRequest
import com.davismariotti.campalert.model.SearchType
import com.davismariotti.campalert.repository.PermitSearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.scheduling.UserAvailabilityProcessedEvent
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

/**
 * Permit analogue of [com.davismariotti.campalert.service.scheduling.AvailabilityChecker]. Reuses the
 * same [UserAvailabilityProcessedEvent] the campground checker publishes — the (already
 * search-type-agnostic) outbox dispatcher reacts to it regardless of which checker fired it.
 */
@Service
class PermitAvailabilityChecker(
    private val permitSearchRequestRepository: PermitSearchRequestRepository,
    private val userRepository: UserRepository,
    private val permitAvailabilityMatcher: PermitAvailabilityMatcher,
    private val permitAvailabilityStateService: PermitAvailabilityStateService,
    private val eventPublisher: ApplicationEventPublisher,
    @Qualifier("availabilityCheckerExecutor") private val executor: Executor,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @SchedulerLock(name = "permitAvailabilityChecker", lockAtMostFor = "PT90S", lockAtLeastFor = "PT30S")
    fun processSearchRequests() {
        val allRequests = permitSearchRequestRepository.findAllIncomplete()

        // Auto-complete requests whose whole window/itinerary has passed, using the permit's local midnight.
        val toComplete = allRequests.filter { req ->
            val lastDay = lastRelevantDay(req)
            lastDay != null && lastDay < today(req.permitTimezone)
        }
        toComplete.forEach { req ->
            req.state.completed = true
            permitSearchRequestRepository.save(req)
        }

        val active = allRequests.filter { req ->
            val lastDay = lastRelevantDay(req)
            (lastDay == null || lastDay >= today(req.permitTimezone)) && req.state.pauseReason == null && req.userId != null
        }
        log.info("Permit availability check started active={} autoCompleted={}", active.size, toComplete.size)
        if (active.isEmpty()) return

        val userMap = userRepository
            .findAllById(active.mapNotNull { it.userId }.toSet())
            .associateBy { it.id!! }
        val valid = active.filter { userMap.containsKey(it.userId) }
        if (valid.isEmpty()) return

        val userCounts = valid.groupBy { it.userId!! }.mapValues { it.value.size }
        val userCountdowns = userCounts.mapValues { AtomicInteger(it.value) }.toMutableMap()
        val userResults = ConcurrentHashMap<Long, MutableList<PermitAvailabilityResult>>()
        userCounts.keys.forEach { uid -> userResults[uid] = mutableListOf() }

        // Tick-scoped dedup caches, shared across every permit search request being processed.
        val zoneCache: ZoneAvailabilityCache = ConcurrentHashMap()
        val itineraryCache: ItineraryAvailabilityCache = ConcurrentHashMap()

        val futures = valid.map { request ->
            CompletableFuture.runAsync(
                {
                    val uid = request.userId!!
                    try {
                        val result = permitAvailabilityMatcher.check(request, zoneCache, itineraryCache)
                        userResults[uid]!!.add(result)
                    } catch (e: Exception) {
                        log.error("Error processing permit requestId={}", request.id, e)
                    } finally {
                        val remaining = userCountdowns[uid]!!.decrementAndGet()
                        if (remaining == 0) {
                            val user = userMap[uid]
                            if (user != null) {
                                val results = userResults[uid] ?: emptyList()
                                try {
                                    permitAvailabilityStateService.processUserResults(results, user)
                                } catch (e: Exception) {
                                    log.error("Error persisting permit state for userId={}", uid, e)
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

    private fun lastRelevantDay(request: PermitSearchRequest): LocalDate? =
        when (request.searchType) {
            SearchType.ZONE -> request.zoneTarget?.endDay
            SearchType.ITINERARY -> request.itineraryTarget?.legs?.maxOfOrNull { it.date }
        }

    private fun today(permitTimezone: String?): LocalDate = LocalDate.now(permitTimezone?.let { ZoneId.of(it) } ?: ZoneOffset.UTC)
}
