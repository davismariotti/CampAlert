package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.model.PermitSearchRequest
import com.davismariotti.campalert.model.SearchType
import com.davismariotti.campalert.repository.PermitSearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.scheduling.UserAvailabilityProcessedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-permit poll check — permit analogue of the old global-tick `PermitAvailabilityChecker`,
 * scoped to a single permit's active requests. See [com.davismariotti.campalert.service.scheduling.CampgroundPollCheckService]
 * for why this runs sequentially rather than fanning requests out onto the executor itself.
 */
@Service
class PermitPollCheckService(
    private val permitSearchRequestRepository: PermitSearchRequestRepository,
    private val userRepository: UserRepository,
    private val permitAvailabilityMatcher: PermitAvailabilityMatcher,
    private val permitAvailabilityStateService: PermitAvailabilityStateService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Returns the number of active requests evaluated this cycle (for instrumentation). */
    fun check(permitId: String): Int {
        val allRequests = permitSearchRequestRepository.findByPermitIdAndCompletedFalse(permitId)
        if (allRequests.isEmpty()) return 0

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
        log.info("Permit poll check permitId={} active={} autoCompleted={}", permitId, active.size, toComplete.size)
        if (active.isEmpty()) return 0

        val userMap = userRepository
            .findAllById(active.mapNotNull { it.userId }.toSet())
            .associateBy { it.id!! }
        val valid = active.filter { userMap.containsKey(it.userId) }
        if (valid.isEmpty()) return 0

        val zoneCache: ZoneAvailabilityCache = ConcurrentHashMap()
        val itineraryCache: ItineraryAvailabilityCache = ConcurrentHashMap()
        val resultsByUser = valid
            .mapNotNull { it.userId }
            .distinct()
            .associateWith { mutableListOf<PermitAvailabilityResult>() }
            .toMutableMap()

        valid.forEach { request ->
            val uid = request.userId!!
            try {
                val result = permitAvailabilityMatcher.check(request, zoneCache, itineraryCache)
                resultsByUser[uid]!!.add(result)
            } catch (e: Exception) {
                log.error("Error processing permit requestId={}", request.id, e)
            }
        }

        resultsByUser.forEach { (uid, results) ->
            val user = userMap[uid] ?: return@forEach
            try {
                permitAvailabilityStateService.processUserResults(results, user)
            } catch (e: Exception) {
                log.error("Error persisting permit state for userId={}", uid, e)
            }
            eventPublisher.publishEvent(UserAvailabilityProcessedEvent(uid))
        }

        return valid.size
    }

    private fun lastRelevantDay(request: PermitSearchRequest): LocalDate? =
        when (request.searchType) {
            SearchType.ZONE -> request.zoneTarget?.endDay
            SearchType.ITINERARY -> request.itineraryTarget?.legs?.maxOfOrNull { it.date }
        }

    private fun today(permitTimezone: String?): LocalDate = LocalDate.now(permitTimezone?.let { ZoneId.of(it) } ?: ZoneOffset.UTC)
}
