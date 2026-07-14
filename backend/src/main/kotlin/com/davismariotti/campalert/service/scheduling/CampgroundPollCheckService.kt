package com.davismariotti.campalert.service.scheduling

import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.availability.AvailabilityResult
import com.davismariotti.campalert.service.availability.CampgroundAvailabilityProviderRegistry
import com.davismariotti.campalert.service.state.AvailabilityStateService
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Per-campground poll check — campground analogue of the old global-tick `AvailabilityChecker`,
 * scoped to a single campground's active requests instead of every incomplete request in the app.
 * Cross-target concurrency now comes from [PollTargetDispatcher] dispatching each claimed target
 * onto the shared executor, so this runs its requests sequentially; the per-campground dedup cache
 * still parallelizes distinct-month fetches within this one cycle exactly as before.
 */
@Service
class CampgroundPollCheckService(
    private val searchRequestRepository: SearchRequestRepository,
    private val userRepository: UserRepository,
    private val campgroundAvailabilityProviderRegistry: CampgroundAvailabilityProviderRegistry,
    private val availabilityStateService: AvailabilityStateService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Returns the number of active requests evaluated this cycle (for instrumentation). */
    fun check(provider: Provider, campsiteId: Int): Int {
        val recreationService = campgroundAvailabilityProviderRegistry.forProvider(provider)
        val allRequests = searchRequestRepository.findByCampsiteIdAndProviderAndCompletedFalse(campsiteId, provider)
        if (allRequests.isEmpty()) return 0

        val toComplete = allRequests.filter { it.startDay < today(it.campgroundTimezone) }
        toComplete.forEach { req ->
            req.state.completed = true
            searchRequestRepository.save(req)
        }

        val active = allRequests.filter { req ->
            req.startDay >= today(req.campgroundTimezone) && req.state.pauseReason == null && req.userId != null
        }
        log.info("Campground poll check campsiteId={} active={} autoCompleted={}", campsiteId, active.size, toComplete.size)
        if (active.isEmpty()) return 0

        val userMap = userRepository
            .findAllById(active.mapNotNull { it.userId }.toSet())
            .associateBy { it.id!! }
        val valid = active.filter { userMap.containsKey(it.userId) }
        if (valid.isEmpty()) return 0

        val cache = recreationService.newCheckCycleCache()
        val resultsByUser = valid
            .mapNotNull { it.userId }
            .distinct()
            .associateWith { mutableListOf<AvailabilityResult>() }
            .toMutableMap()

        valid.forEach { request ->
            val uid = request.userId!!
            try {
                val user = userMap[uid]!!
                val result = recreationService.checkAvailability(request, user, cache)
                resultsByUser[uid]!!.add(result)
            } catch (e: Exception) {
                log.error("Error processing requestId={}", request.id, e)
            }
        }

        resultsByUser.forEach { (uid, results) ->
            val user = userMap[uid] ?: return@forEach
            try {
                availabilityStateService.processUserResults(results, user)
            } catch (e: Exception) {
                log.error("Error persisting state for userId={}", uid, e)
            }
            eventPublisher.publishEvent(UserAvailabilityProcessedEvent(uid))
        }

        return valid.size
    }

    private fun today(campgroundTimezone: String?): LocalDate = LocalDate.now(campgroundTimezone?.let { ZoneId.of(it) } ?: ZoneOffset.UTC)
}
