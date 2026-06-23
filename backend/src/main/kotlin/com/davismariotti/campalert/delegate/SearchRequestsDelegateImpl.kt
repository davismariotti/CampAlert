package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.SearchRequestsApiDelegate
import com.davismariotti.campalert.api.model.CreateSearchRequestBody
import com.davismariotti.campalert.api.model.ErrorResponse
import com.davismariotti.campalert.api.model.SearchRequestResponse
import com.davismariotti.campalert.api.model.SearchRequestStats
import com.davismariotti.campalert.api.model.UpdateSearchRequestBody
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.repository.NotificationOutboxRepository
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.SearchRequestCheckRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.TimezoneResolutionService
import com.davismariotti.campalert.util.currentUserId
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service

@Service
class SearchRequestsDelegateImpl(
    private val searchRequestRepository: SearchRequestRepository,
    private val userRepository: UserRepository,
    private val phoneNumberRepository: PhoneNumberRepository,
    private val searchRequestCheckRepository: SearchRequestCheckRepository,
    private val notificationOutboxRepository: NotificationOutboxRepository,
    private val timezoneResolutionService: TimezoneResolutionService,
) : SearchRequestsApiDelegate {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun currentUserId(): Long = currentUserId(userRepository)

    @PreAuthorize("isAuthenticated()")
    override fun listSearchRequests(completed: Boolean?): ResponseEntity<List<SearchRequestResponse>> {
        val userId = currentUserId()
        val results = if (completed != null) {
            searchRequestRepository.findByCompletedAndUserId(completed, userId)
        } else {
            searchRequestRepository.findByUserId(userId)
        }
        if (results.isEmpty()) return ResponseEntity.ok(emptyList())

        val ids = results.map { it.id!! }
        val countStatsById = searchRequestCheckRepository.findCountStatsByRequestIds(ids).associateBy { it.getSearchRequestId() }
        val avgWindowById = searchRequestCheckRepository.findAvgWindowByRequestIds(ids).associateBy { it.getSearchRequestId() }
        val missedById = notificationOutboxRepository.findMissedWindowCountsByRequestIds(ids).associateBy { it.getRequestId() }

        return ResponseEntity.ok(
            results.map { request ->
                val id = request.id!!
                val counts = countStatsById[id]
                val total = counts?.getTotalChecks() ?: 0L
                val available = counts?.getAvailableChecks() ?: 0L
                val avgWindow = avgWindowById[id]?.getAvgWindowMinutes() ?: 0.0
                val missed = missedById[id]?.getMissedCount() ?: 0L
                val stats = SearchRequestStats(
                    totalChecks = total,
                    availableChecks = available,
                    avgAvailabilityWindowMinutes = avgWindow,
                    missedQuietHoursWindows = missed,
                    availabilityRate = if (total > 0) available.toDouble() / total.toDouble() else null,
                )
                request.toResponse(stats)
            },
        )
    }

    @Suppress("UNCHECKED_CAST")
    @PreAuthorize("isAuthenticated()")
    override fun createSearchRequest(
        createSearchRequestBody: CreateSearchRequestBody,
    ): ResponseEntity<SearchRequestResponse> {
        val userId = currentUserId()
        if (phoneNumberRepository.countByUserIdAndStatus(userId, PhoneNumberStatus.VERIFIED) == 0L) {
            return ResponseEntity.status(422).body(
                ErrorResponse(
                    message = "A verified phone number is required to create a search request.",
                    code = "NO_VERIFIED_PHONE",
                ),
            ) as ResponseEntity<SearchRequestResponse>
        }
        val entity = SearchRequest(
            startDay = createSearchRequestBody.startDay,
            nights = createSearchRequestBody.nights,
            groupSize = createSearchRequestBody.groupSize,
            campsiteId = createSearchRequestBody.campsiteId,
            campgroundName = createSearchRequestBody.campgroundName,
            loops = createSearchRequestBody.loops,
            name = createSearchRequestBody.name,
            completed = false,
            userId = userId,
        )
        val savedRequest = searchRequestRepository.save(entity)
        log.info("Search request created userId={} requestId={} campsiteId={} nights={}", userId, savedRequest.id, savedRequest.campsiteId, savedRequest.nights)
        timezoneResolutionService.resolveAndPersistAsync(savedRequest.id!!, createSearchRequestBody.campsiteId)
        return ResponseEntity.status(201).body(savedRequest.toResponse(fetchStats(savedRequest.id!!)))
    }

    @PreAuthorize("isAuthenticated()")
    override fun getSearchRequest(id: Long): ResponseEntity<SearchRequestResponse> {
        val userId = currentUserId()
        val entity = searchRequestRepository
            .findById(id)
            .orElse(null)
            ?.takeIf { it.userId == userId }
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(entity.toResponse(fetchStats(id)))
    }

    @PreAuthorize("isAuthenticated()")
    override fun updateSearchRequest(
        id: Long,
        updateSearchRequestBody: UpdateSearchRequestBody,
    ): ResponseEntity<SearchRequestResponse> {
        val userId = currentUserId()
        val existing = searchRequestRepository
            .findById(id)
            .orElse(null)
            ?.takeIf { it.userId == userId }
            ?: return ResponseEntity.notFound().build()
        val updated = existing.copy(
            startDay = updateSearchRequestBody.startDay,
            nights = updateSearchRequestBody.nights,
            groupSize = updateSearchRequestBody.groupSize,
            campsiteId = updateSearchRequestBody.campsiteId,
            loops = updateSearchRequestBody.loops,
            name = updateSearchRequestBody.name,
            completed = updateSearchRequestBody.completed,
        )
        val saved = searchRequestRepository.save(updated)
        return ResponseEntity.ok(saved.toResponse(fetchStats(id)))
    }

    @PreAuthorize("isAuthenticated()")
    override fun deleteSearchRequest(id: Long): ResponseEntity<Unit> {
        val userId = currentUserId()
        searchRequestRepository
            .findById(id)
            .orElse(null)
            ?.takeIf { it.userId == userId }
            ?: return ResponseEntity.notFound().build()
        searchRequestRepository.deleteById(id)
        return ResponseEntity.noContent().build()
    }

    private fun fetchStats(requestId: Long): SearchRequestStats {
        val total = searchRequestCheckRepository.countBySearchRequestId(requestId)
        val available = searchRequestCheckRepository.countAvailableBySearchRequestId(requestId)
        return SearchRequestStats(
            totalChecks = total,
            availableChecks = available,
            avgAvailabilityWindowMinutes = if (total > 0) searchRequestCheckRepository.computeAvgWindowMinutes(requestId) else 0.0,
            missedQuietHoursWindows = notificationOutboxRepository.countMissedWindowsByRequestId(requestId),
            availabilityRate = if (total > 0) available.toDouble() / total.toDouble() else null,
        )
    }

    private fun SearchRequest.toResponse(stats: SearchRequestStats): SearchRequestResponse =
        SearchRequestResponse(
            id = this.id ?: error("Cannot map unsaved search request"),
            startDay = this.startDay,
            nights = this.nights,
            groupSize = this.groupSize,
            campsiteId = this.campsiteId,
            campgroundName = this.campgroundName,
            loops = this.loops,
            name = this.name,
            completed = this.completed,
            pauseReason = this.pauseReason,
            stats = stats,
        )
}
