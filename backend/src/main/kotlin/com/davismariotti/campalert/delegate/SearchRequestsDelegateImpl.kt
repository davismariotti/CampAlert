package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.SearchRequestsApiDelegate
import com.davismariotti.campalert.api.model.CreateSearchRequestBody
import com.davismariotti.campalert.api.model.ErrorResponse
import com.davismariotti.campalert.api.model.SearchRequestResponse
import com.davismariotti.campalert.api.model.SearchRequestStats
import com.davismariotti.campalert.api.model.UpdateSearchRequestBody
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.model.RequestType
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.SearchRequestState
import com.davismariotti.campalert.repository.NotificationOutboxRepository
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.TimezoneResolutionService
import com.davismariotti.campalert.service.scheduling.PollTargetRegistrationService
import com.davismariotti.campalert.util.currentUserId
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SearchRequestsDelegateImpl(
    private val searchRequestRepository: SearchRequestRepository,
    private val userRepository: UserRepository,
    private val phoneNumberRepository: PhoneNumberRepository,
    private val notificationOutboxRepository: NotificationOutboxRepository,
    private val timezoneResolutionService: TimezoneResolutionService,
    private val pollTargetRegistrationService: PollTargetRegistrationService,
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
        val missedById = notificationOutboxRepository
            .findMissedWindowCountsByRequestTypeAndRequestIds(RequestType.CAMPGROUND, ids)
            .associateBy { it.getRequestId() }

        return ResponseEntity.ok(
            results.map { request ->
                val id = request.id!!
                val s = request.state
                val total = s.totalChecks.toLong()
                val available = s.availableChecks.toLong()
                val missed = missedById[id]?.getMissedCount() ?: 0L
                val stats = SearchRequestStats(
                    totalChecks = total,
                    availableChecks = available,
                    avgAvailabilityWindowMinutes = if (s.windowCount > 0) (s.totalWindowSeconds / 60.0) / s.windowCount else 0.0,
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
            userId = userId,
        )
        val state = SearchRequestState()
        state.searchRequest = entity
        entity.state = state
        val savedRequest = searchRequestRepository.save(entity)
        log.info("Search request created userId={} requestId={} campsiteId={} nights={}", userId, savedRequest.id, savedRequest.campsiteId, savedRequest.nights)
        pollTargetRegistrationService.ensureCampgroundTarget(savedRequest.campsiteId)
        timezoneResolutionService.resolveAndPersistAsync(savedRequest.id!!, createSearchRequestBody.campsiteId)
        return ResponseEntity.status(201).body(savedRequest.toResponse(fetchStats(savedRequest)))
    }

    @PreAuthorize("isAuthenticated()")
    override fun getSearchRequest(id: Long): ResponseEntity<SearchRequestResponse> {
        val userId = currentUserId()
        val entity = searchRequestRepository
            .findById(id)
            .orElse(null)
            ?.takeIf { it.userId == userId }
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(entity.toResponse(fetchStats(entity)))
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
        )
        // state is a body property excluded from copy(); transfer reference and apply state mutation
        updated.state = existing.state
        updated.state.completed = updateSearchRequestBody.completed
        val saved = searchRequestRepository.save(updated)
        pollTargetRegistrationService.ensureCampgroundTarget(saved.campsiteId)
        return ResponseEntity.ok(saved.toResponse(fetchStats(saved)))
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    override fun deleteSearchRequest(id: Long): ResponseEntity<Unit> {
        val userId = currentUserId()
        searchRequestRepository
            .findById(id)
            .orElse(null)
            ?.takeIf { it.userId == userId }
            ?: return ResponseEntity.notFound().build()
        notificationOutboxRepository.deleteByRequestTypeAndRequestId(RequestType.CAMPGROUND, id)
        searchRequestRepository.deleteById(id)
        return ResponseEntity.noContent().build()
    }

    private fun fetchStats(request: SearchRequest): SearchRequestStats {
        val s = request.state
        val total = s.totalChecks.toLong()
        val available = s.availableChecks.toLong()
        return SearchRequestStats(
            totalChecks = total,
            availableChecks = available,
            avgAvailabilityWindowMinutes = if (s.windowCount > 0) (s.totalWindowSeconds / 60.0) / s.windowCount else 0.0,
            missedQuietHoursWindows = notificationOutboxRepository.countMissedWindowsByRequestTypeAndRequestId(RequestType.CAMPGROUND, request.id!!),
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
            completed = this.state.completed,
            pauseReason = this.state.pauseReason,
            stats = stats,
        )
}
