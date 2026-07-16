package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.PermitSearchRequestsApiDelegate
import com.davismariotti.campalert.api.model.CreatePermitSearchRequestBody
import com.davismariotti.campalert.api.model.ErrorResponse
import com.davismariotti.campalert.api.model.PermitItineraryLegBody
import com.davismariotti.campalert.api.model.PermitItineraryTargetResponse
import com.davismariotti.campalert.api.model.PermitSearchRequestResponse
import com.davismariotti.campalert.api.model.PermitType
import com.davismariotti.campalert.api.model.PermitZoneTargetResponse
import com.davismariotti.campalert.api.model.SearchRequestStats
import com.davismariotti.campalert.api.model.UpdatePermitSearchRequestBody
import com.davismariotti.campalert.model.PermitItineraryLeg
import com.davismariotti.campalert.model.PermitItineraryTarget
import com.davismariotti.campalert.model.PermitSearchRequest
import com.davismariotti.campalert.model.PermitSearchRequestState
import com.davismariotti.campalert.model.PermitZoneTarget
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.model.RequestType
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.repository.NotificationOutboxRepository
import com.davismariotti.campalert.repository.PermitSearchRequestRepository
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.permit.ItineraryLegValidator
import com.davismariotti.campalert.service.permit.LegValidationResult
import com.davismariotti.campalert.service.permit.PermitClassificationService
import com.davismariotti.campalert.service.permit.PermitContentCache
import com.davismariotti.campalert.service.scheduling.PollTargetRegistrationService
import com.davismariotti.campalert.service.turnstile.TurnstileService
import com.davismariotti.campalert.util.currentUserId
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class PermitSearchRequestsDelegateImpl(
    private val permitSearchRequestRepository: PermitSearchRequestRepository,
    private val userRepository: UserRepository,
    private val phoneNumberRepository: PhoneNumberRepository,
    private val notificationOutboxRepository: NotificationOutboxRepository,
    private val permitClassificationService: PermitClassificationService,
    private val permitContentCache: PermitContentCache,
    private val pollTargetRegistrationService: PollTargetRegistrationService,
    private val turnstileService: TurnstileService,
) : PermitSearchRequestsApiDelegate {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun currentUserId(): Long = currentUserId(userRepository)

    @PreAuthorize("isAuthenticated()")
    override fun listPermitSearchRequests(completed: Boolean?): ResponseEntity<List<PermitSearchRequestResponse>> {
        val userId = currentUserId()
        val results = if (completed != null) {
            permitSearchRequestRepository.findByCompletedAndUserId(completed, userId)
        } else {
            permitSearchRequestRepository.findByUserId(userId)
        }
        return ResponseEntity.ok(results.map { it.toResponse(fetchStats(it)) })
    }

    @Suppress("UNCHECKED_CAST")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    override fun createPermitSearchRequest(
        createPermitSearchRequestBody: CreatePermitSearchRequestBody,
    ): ResponseEntity<PermitSearchRequestResponse> {
        turnstileService.verify(createPermitSearchRequestBody.turnstileToken)
        val userId = currentUserId()
        if (phoneNumberRepository.countByUserIdAndStatus(userId, PhoneNumberStatus.VERIFIED) == 0L) {
            return ResponseEntity.status(422).body(
                ErrorResponse(message = "A verified phone number is required to create a search request.", code = "NO_VERIFIED_PHONE"),
            ) as ResponseEntity<PermitSearchRequestResponse>
        }

        val body = createPermitSearchRequestBody
        validateShape(body.searchType, body.zoneTarget != null, body.itineraryTarget != null)

        val classifiedType = permitClassificationService.classify(body.permitId)
            ?: return unsupportedPermitResponse()
        if (classifiedType.toApi() != body.searchType) {
            return searchTypeMismatchResponse(classifiedType.toApi())
        }

        if (body.searchType == PermitType.ITINERARY) {
            validateLegs(body.permitId, body.itineraryTarget!!.legs)?.let { return it }
        }

        val entity = PermitSearchRequest(
            permitId = body.permitId,
            permitName = body.permitName,
            groupSize = body.groupSize,
            name = body.name,
            userId = userId,
            searchType = classifiedType,
            provider = body.provider?.type?.toModel() ?: Provider.RECREATION_GOV,
        )
        val state = PermitSearchRequestState()
        state.permitSearchRequest = entity
        entity.state = state
        applyTargets(entity, body.zoneTarget, body.itineraryTarget)

        val saved = permitSearchRequestRepository.save(entity)
        log.info("Permit search request created userId={} requestId={} permitId={} searchType={}", userId, saved.id, saved.permitId, saved.searchType)
        pollTargetRegistrationService.ensurePermitTarget(saved.permitId, saved.provider)
        return ResponseEntity.status(201).body(saved.toResponse(fetchStats(saved)))
    }

    @PreAuthorize("isAuthenticated()")
    override fun getPermitSearchRequest(id: Long): ResponseEntity<PermitSearchRequestResponse> {
        val userId = currentUserId()
        val entity = permitSearchRequestRepository
            .findById(id)
            .orElse(null)
            ?.takeIf { it.userId == userId }
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(entity.toResponse(fetchStats(entity)))
    }

    @Suppress("UNCHECKED_CAST")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    override fun updatePermitSearchRequest(
        id: Long,
        updatePermitSearchRequestBody: UpdatePermitSearchRequestBody,
    ): ResponseEntity<PermitSearchRequestResponse> {
        val userId = currentUserId()
        val existing = permitSearchRequestRepository
            .findById(id)
            .orElse(null)
            ?.takeIf { it.userId == userId }
            ?: return ResponseEntity.notFound().build()

        val body = updatePermitSearchRequestBody
        validateShape(body.searchType, body.zoneTarget != null, body.itineraryTarget != null)

        val classifiedType = permitClassificationService.classify(body.permitId)
            ?: return unsupportedPermitResponse()
        if (classifiedType.toApi() != body.searchType) {
            return searchTypeMismatchResponse(classifiedType.toApi())
        }

        if (body.searchType == PermitType.ITINERARY) {
            validateLegs(body.permitId, body.itineraryTarget!!.legs)?.let { return it }
        }

        val updated = existing.copy(
            permitId = body.permitId,
            permitName = body.permitName,
            groupSize = body.groupSize,
            name = body.name,
            searchType = classifiedType,
            provider = body.provider?.type?.toModel() ?: existing.provider,
        )
        // state/zoneTarget/itineraryTarget are body properties excluded from copy(); transfer
        // references before applyTargets() so it mutates the existing rows in place rather than
        // detaching them (orphanRemoval would otherwise delete-then-reinsert on the same PK).
        updated.state = existing.state
        updated.state.completed = body.completed
        updated.zoneTarget = existing.zoneTarget
        updated.itineraryTarget = existing.itineraryTarget
        applyTargets(updated, body.zoneTarget, body.itineraryTarget)

        val saved = permitSearchRequestRepository.save(updated)
        pollTargetRegistrationService.ensurePermitTarget(saved.permitId, saved.provider)
        return ResponseEntity.ok(saved.toResponse(fetchStats(saved)))
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    override fun deletePermitSearchRequest(id: Long): ResponseEntity<Unit> {
        val userId = currentUserId()
        permitSearchRequestRepository
            .findById(id)
            .orElse(null)
            ?.takeIf { it.userId == userId }
            ?: return ResponseEntity.notFound().build()
        notificationOutboxRepository.deleteByRequestTypeAndRequestId(RequestType.PERMIT, id)
        permitSearchRequestRepository.deleteById(id)
        return ResponseEntity.noContent().build()
    }

    // --- validation helpers ---

    private fun validateShape(searchType: PermitType, hasZoneTarget: Boolean, hasItineraryTarget: Boolean) {
        val valid = when (searchType) {
            PermitType.ZONE -> hasZoneTarget && !hasItineraryTarget
            PermitType.ITINERARY -> hasItineraryTarget && !hasZoneTarget
        }
        if (valid) return
        throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Request body must include exactly the target matching searchType=$searchType",
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun unsupportedPermitResponse(): ResponseEntity<PermitSearchRequestResponse> =
        ResponseEntity.status(422).body(
            ErrorResponse(message = "Permit reservation mechanism is not supported", code = "PERMIT_TYPE_NOT_SUPPORTED"),
        ) as ResponseEntity<PermitSearchRequestResponse>

    @Suppress("UNCHECKED_CAST")
    private fun searchTypeMismatchResponse(actual: PermitType): ResponseEntity<PermitSearchRequestResponse> =
        ResponseEntity.status(422).body(
            ErrorResponse(message = "Permit is classified as $actual, not the requested searchType", code = "PERMIT_TYPE_MISMATCH"),
        ) as ResponseEntity<PermitSearchRequestResponse>

    @Suppress("UNCHECKED_CAST")
    private fun validateLegs(permitId: String, legs: List<PermitItineraryLegBody>): ResponseEntity<PermitSearchRequestResponse>? {
        val content = permitContentCache.get(permitId)
            ?: throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Recreation.gov upstream error")
        return when (val result = ItineraryLegValidator.validate(content, legs.map { it.divisionId })) {
            LegValidationResult.Valid -> null
            is LegValidationResult.Invalid ->
                ResponseEntity.status(422).body(
                    ErrorResponse(
                        message = "Leg ${result.legIndex} (division ${result.divisionId}) is not a legal continuation of the previous leg",
                        code = "ILLEGAL_LEG_SEQUENCE",
                    ),
                ) as ResponseEntity<PermitSearchRequestResponse>
        }
    }

    private fun applyTargets(
        entity: PermitSearchRequest,
        zoneTargetBody: com.davismariotti.campalert.api.model.PermitZoneTargetBody?,
        itineraryTargetBody: com.davismariotti.campalert.api.model.PermitItineraryTargetBody?,
    ) {
        if (zoneTargetBody != null) {
            val target = entity.zoneTarget ?: PermitZoneTarget()
            target.permitSearchRequest = entity
            target.divisionIds = zoneTargetBody.divisionIds
            target.startDay = zoneTargetBody.startDay
            target.endDay = zoneTargetBody.endDay
            entity.zoneTarget = target
            entity.itineraryTarget = null
        } else if (itineraryTargetBody != null) {
            val target = entity.itineraryTarget ?: PermitItineraryTarget()
            target.permitSearchRequest = entity
            target.legs = itineraryTargetBody.legs.map { PermitItineraryLeg(it.divisionId, it.date) }
            entity.itineraryTarget = target
            entity.zoneTarget = null
        }
    }

    // --- response mapping ---

    private fun fetchStats(request: PermitSearchRequest): SearchRequestStats {
        val s = request.state
        val total = s.totalChecks.toLong()
        val available = s.availableChecks.toLong()
        return SearchRequestStats(
            totalChecks = total,
            availableChecks = available,
            avgAvailabilityWindowMinutes = if (s.windowCount > 0) (s.totalWindowSeconds / 60.0) / s.windowCount else 0.0,
            missedQuietHoursWindows = notificationOutboxRepository.countMissedWindowsByRequestTypeAndRequestId(RequestType.PERMIT, request.id!!),
            availabilityRate = if (total > 0) available.toDouble() / total.toDouble() else null,
        )
    }

    private fun PermitSearchRequest.toResponse(stats: SearchRequestStats): PermitSearchRequestResponse =
        PermitSearchRequestResponse(
            id = this.id ?: error("Cannot map unsaved permit search request"),
            permitId = this.permitId,
            permitName = this.permitName,
            permitTimezone = this.permitTimezone,
            groupSize = this.groupSize,
            name = this.name,
            searchType = this.searchType.toApi(),
            zoneTarget = this.zoneTarget?.let { t ->
                PermitZoneTargetResponse(
                    divisionIds = t.divisionIds,
                    startDay = t.startDay,
                    endDay = t.endDay,
                    matchedDivisionId = this.state.matchedDivisionId,
                    matchedDate = this.state.matchedDate,
                )
            },
            itineraryTarget = this.itineraryTarget?.let { t ->
                PermitItineraryTargetResponse(
                    legs = t.legs.map { PermitItineraryLegBody(divisionId = it.divisionId, date = it.date) },
                    blockingDivisionId = this.state.blockingDivisionId,
                    blockingDate = this.state.blockingDate,
                )
            },
            completed = this.state.completed,
            pauseReason = this.state.pauseReason,
            stats = stats,
            provider = this.provider.toApi(),
        )
}
