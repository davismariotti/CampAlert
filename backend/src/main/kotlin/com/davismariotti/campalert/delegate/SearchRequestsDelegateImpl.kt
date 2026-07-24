package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.SearchRequestsApiDelegate
import com.davismariotti.campalert.api.model.CreateSearchRequestBody
import com.davismariotti.campalert.api.model.SearchRequestResponse
import com.davismariotti.campalert.api.model.SearchRequestStats
import com.davismariotti.campalert.api.model.UpdateSearchRequestBody
import com.davismariotti.campalert.exception.FlexibleSearchValidationException
import com.davismariotti.campalert.exception.NoVerifiedPhoneException
import com.davismariotti.campalert.exception.NotFoundException
import com.davismariotti.campalert.model.CampLifeSearchRequestDetails
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.model.RecreationGovSearchRequestDetails
import com.davismariotti.campalert.model.RequestType
import com.davismariotti.campalert.model.ReserveCaliforniaSearchRequestDetails
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.SearchRequestState
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.provider.camplife.CampLifeCatalogCache
import com.davismariotti.campalert.provider.reservecalifornia.ReserveCaliforniaCatalogCache
import com.davismariotti.campalert.provider.reservecalifornia.ReserveCaliforniaOccupancyService
import com.davismariotti.campalert.repository.NotificationOutboxRepository
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.TimezoneResolutionService
import com.davismariotti.campalert.service.scheduling.PollTargetRegistrationService
import com.davismariotti.campalert.service.scheduling.ProviderSearchWindowProperties
import com.davismariotti.campalert.service.turnstile.TurnstileService
import com.davismariotti.campalert.util.currentUserId
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class SearchRequestsDelegateImpl(
    private val searchRequestRepository: SearchRequestRepository,
    private val userRepository: UserRepository,
    private val phoneNumberRepository: PhoneNumberRepository,
    private val notificationOutboxRepository: NotificationOutboxRepository,
    private val timezoneResolutionService: TimezoneResolutionService,
    private val pollTargetRegistrationService: PollTargetRegistrationService,
    private val campLifeCatalogCache: CampLifeCatalogCache,
    private val reserveCaliforniaCatalogCache: ReserveCaliforniaCatalogCache,
    private val reserveCaliforniaOccupancyService: ReserveCaliforniaOccupancyService,
    private val providerSearchWindowProperties: ProviderSearchWindowProperties,
    private val turnstileService: TurnstileService,
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

    @PreAuthorize("isAuthenticated()")
    override fun createSearchRequest(
        createSearchRequestBody: CreateSearchRequestBody,
    ): ResponseEntity<SearchRequestResponse> {
        turnstileService.verify(createSearchRequestBody.turnstileToken)
        val userId = currentUserId()
        if (phoneNumberRepository.countByUserIdAndStatus(userId, PhoneNumberStatus.VERIFIED) == 0L) {
            throw NoVerifiedPhoneException()
        }
        val provider = createSearchRequestBody.provider?.type?.toModel() ?: Provider.RECREATION_GOV
        validateLatestStartDay(createSearchRequestBody.startDay, createSearchRequestBody.latestStartDay, provider)
        val entity = SearchRequest(
            startDay = createSearchRequestBody.startDay,
            nights = createSearchRequestBody.nights,
            groupSize = createSearchRequestBody.groupSize,
            campsiteId = createSearchRequestBody.campsiteId,
            campgroundName = createSearchRequestBody.campgroundName,
            siteIds = createSearchRequestBody.siteIds,
            name = createSearchRequestBody.name,
            userId = userId,
            provider = provider,
            latestStartDay = createSearchRequestBody.latestStartDay,
        )
        val state = SearchRequestState()
        state.searchRequest = entity
        entity.state = state
        applyProviderDetails(entity, provider, createSearchRequestBody.loops, createSearchRequestBody.amenityIds)
        val savedRequest = searchRequestRepository.save(entity)
        log.info("Search request created userId={} requestId={} campsiteId={} nights={}", userId, savedRequest.id, savedRequest.campsiteId, savedRequest.nights)
        pollTargetRegistrationService.ensureCampgroundTarget(savedRequest.campsiteId, savedRequest.provider)
        timezoneResolutionService.resolveAndPersistAsync(savedRequest.id!!, createSearchRequestBody.campsiteId, savedRequest.provider)
        return ResponseEntity.status(201).body(savedRequest.toResponse(fetchStats(savedRequest)))
    }

    @PreAuthorize("isAuthenticated()")
    override fun getSearchRequest(id: Long): ResponseEntity<SearchRequestResponse> {
        val userId = currentUserId()
        val entity = searchRequestRepository
            .findById(id)
            .orElse(null)
            ?.takeIf { it.userId == userId }
            ?: throw NotFoundException("Search request not found")
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
            ?: throw NotFoundException("Search request not found")
        val provider = updateSearchRequestBody.provider?.type?.toModel() ?: existing.provider
        validateLatestStartDay(updateSearchRequestBody.startDay, updateSearchRequestBody.latestStartDay, provider)
        val updated = existing.copy(
            startDay = updateSearchRequestBody.startDay,
            nights = updateSearchRequestBody.nights,
            groupSize = updateSearchRequestBody.groupSize,
            campsiteId = updateSearchRequestBody.campsiteId,
            siteIds = updateSearchRequestBody.siteIds,
            name = updateSearchRequestBody.name,
            provider = provider,
            latestStartDay = updateSearchRequestBody.latestStartDay,
        )
        // state/recreationGovDetails/campLifeDetails are body properties excluded from copy(); transfer
        // references before applyProviderDetails() so it mutates the existing rows in place rather than
        // detaching them (orphanRemoval would otherwise delete-then-reinsert on the same PK).
        updated.state = existing.state
        updated.state.completed = updateSearchRequestBody.completed
        updated.recreationGovDetails = existing.recreationGovDetails
        updated.campLifeDetails = existing.campLifeDetails
        updated.reserveCaliforniaDetails = existing.reserveCaliforniaDetails
        applyProviderDetails(updated, provider, updateSearchRequestBody.loops, updateSearchRequestBody.amenityIds)
        val saved = searchRequestRepository.save(updated)
        pollTargetRegistrationService.ensureCampgroundTarget(saved.campsiteId, saved.provider)
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
            ?: throw NotFoundException("Search request not found")
        notificationOutboxRepository.deleteByRequestTypeAndRequestId(RequestType.CAMPGROUND, id)
        searchRequestRepository.deleteById(id)
        return ResponseEntity.noContent().build()
    }

    /**
     * Validates an optional flexible-search `latestStartDay`: it must be on or after `startDay` (every
     * date in between is a legitimate candidate arrival, regardless of `nights`), and the resulting
     * range width must fit within the provider's configured `max-range-width-days` (a provider with no
     * configured max does not support flexible search at all).
     */
    private fun validateLatestStartDay(
        startDay: LocalDate,
        latestStartDay: LocalDate?,
        provider: Provider
    ) {
        if (latestStartDay == null) return
        if (latestStartDay.isBefore(startDay)) {
            throw FlexibleSearchValidationException.LatestStartDayTooEarly()
        }
        val maxRangeWidthDays = providerSearchWindowProperties.maxRangeWidthDaysFor(provider)
            ?: throw FlexibleSearchValidationException.Unsupported(provider.friendlyName)
        val rangeWidthDays = ChronoUnit.DAYS.between(startDay, latestStartDay)
        if (rangeWidthDays > maxRangeWidthDays) {
            throw FlexibleSearchValidationException.RangeTooWide(rangeWidthDays, maxRangeWidthDays, provider.friendlyName)
        }
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

    /**
     * Persists provider-specific grouping/amenity data into the correct details table
     * (`recreation_gov_search_request_details` / `camplife_search_request_details`), clearing the
     * other provider's table — mirrors [PermitSearchRequestsDelegateImpl]'s `applyTargets` pattern.
     * `loops` stays a provider-agnostic wire field (grouping selection by name); for CampLife it's
     * resolved to the single `siteTypeId` its availability endpoint actually accepts.
     */
    private fun applyProviderDetails(
        entity: SearchRequest,
        provider: Provider,
        loops: List<String>?,
        amenityIds: List<Int>?
    ) {
        when (provider) {
            Provider.RECREATION_GOV -> {
                entity.campLifeDetails = null
                entity.recreationGovDetails = if (!loops.isNullOrEmpty()) {
                    val details = entity.recreationGovDetails ?: RecreationGovSearchRequestDetails()
                    details.searchRequest = entity
                    details.loops = loops
                    details
                } else {
                    null
                }
            }
            Provider.CAMPLIFE -> {
                entity.recreationGovDetails = null
                val siteTypeId = loops?.firstOrNull()?.let { resolveCampLifeSiteTypeId(entity.campsiteId, it) }
                val nonEmptyAmenityIds = amenityIds?.takeIf { it.isNotEmpty() }
                entity.campLifeDetails = if (siteTypeId != null || nonEmptyAmenityIds != null) {
                    val details = entity.campLifeDetails ?: CampLifeSearchRequestDetails()
                    details.searchRequest = entity
                    details.siteTypeId = siteTypeId
                    details.amenityIds = nonEmptyAmenityIds
                    details
                } else {
                    null
                }
            }
            Provider.RESERVE_CALIFORNIA -> {
                entity.recreationGovDetails = null
                entity.campLifeDetails = null
                val placeId = reserveCaliforniaCatalogCache
                    .getDirectory()
                    .find { it.facilityId == entity.campsiteId }
                    ?.placeId
                    ?: throw NotFoundException("ReserveCalifornia facility not found")
                val groupIds = loops
                    ?.let { resolveReserveCaliforniaGroupIds(entity.campsiteId, it) }
                    ?.takeIf { it.isNotEmpty() }
                val nonEmptyAmenityIds = amenityIds?.takeIf { it.isNotEmpty() }
                val details = entity.reserveCaliforniaDetails ?: ReserveCaliforniaSearchRequestDetails()
                details.searchRequest = entity
                details.placeId = placeId
                details.unitTypeGroupIds = groupIds
                details.amenityIds = nonEmptyAmenityIds
                entity.reserveCaliforniaDetails = details

                // D13: any-site groupSize-scoped searches need the facility's whole roster warmed up;
                // site_ids-scoped searches resolve occupancy lazily inside checkAvailability itself
                // (D17) and never trigger this.
                if (entity.siteIds.isNullOrEmpty() && entity.groupSize > 1) {
                    reserveCaliforniaCatalogCache.getFacilityRoster(entity.campsiteId)?.let { roster ->
                        reserveCaliforniaOccupancyService.ensureWarmingUp(entity.campsiteId, roster.units)
                    }
                }
            }
        }
    }

    private fun resolveCampLifeSiteTypeId(campgroundId: Int, groupingName: String): Int? =
        campLifeCatalogCache
            .getCampgroundCatalog(campgroundId)
            ?.config
            ?.siteTypes
            ?.find { it.name.equals(groupingName, ignoreCase = true) }
            ?.id

    private fun resolveCampLifeSiteTypeName(campgroundId: Int, siteTypeId: Int): String? =
        campLifeCatalogCache
            .getCampgroundCatalog(campgroundId)
            ?.config
            ?.siteTypes
            ?.find { it.id == siteTypeId }
            ?.name

    /** ReserveCalifornia's loop concept (D5) is `UnitTypeGroupId`, scoped per parent park — resolved via the facility's directory entry then that park's cached filters. */
    private fun reserveCaliforniaGroupNamesByGroupId(facilityId: Int): Map<Int, String> {
        val placeId = reserveCaliforniaCatalogCache.getDirectory().find { it.facilityId == facilityId }?.placeId ?: return emptyMap()
        return reserveCaliforniaCatalogCache
            .getParkFilters(placeId)
            ?.unitTypesGroups
            ?.associate { it.unitTypesGroupId to it.unitTypesGroupName }
            ?: emptyMap()
    }

    private fun resolveReserveCaliforniaGroupIds(facilityId: Int, loopNames: List<String>): List<Int> {
        val idsByName = reserveCaliforniaGroupNamesByGroupId(facilityId).entries.associate { (id, name) -> name.lowercase() to id }
        return loopNames.mapNotNull { idsByName[it.lowercase()] }
    }

    private fun resolveReserveCaliforniaGroupNames(facilityId: Int, groupIds: List<Int>): List<String> {
        val namesById = reserveCaliforniaGroupNamesByGroupId(facilityId)
        return groupIds.mapNotNull { namesById[it] }
    }

    private fun SearchRequest.resolveLoops(): List<String>? =
        when (provider) {
            Provider.RECREATION_GOV -> recreationGovDetails?.loops
            Provider.CAMPLIFE ->
                campLifeDetails
                    ?.siteTypeId
                    ?.let { resolveCampLifeSiteTypeName(campsiteId, it) }
                    ?.let { listOf(it) }
            Provider.RESERVE_CALIFORNIA ->
                reserveCaliforniaDetails
                    ?.unitTypeGroupIds
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { resolveReserveCaliforniaGroupNames(campsiteId, it) }
                    ?.takeIf { it.isNotEmpty() }
        }

    private fun SearchRequest.toResponse(stats: SearchRequestStats): SearchRequestResponse =
        SearchRequestResponse(
            id = this.id ?: error("Cannot map unsaved search request"),
            startDay = this.startDay,
            nights = this.nights,
            groupSize = this.groupSize,
            campsiteId = this.campsiteId,
            campgroundName = this.campgroundName,
            loops = this.resolveLoops(),
            siteIds = this.siteIds,
            amenityIds = when (this.provider) {
                Provider.CAMPLIFE -> this.campLifeDetails?.amenityIds
                Provider.RESERVE_CALIFORNIA -> this.reserveCaliforniaDetails?.amenityIds
                Provider.RECREATION_GOV -> null
            },
            name = this.name,
            completed = this.state.completed,
            pauseReason = this.state.pauseReason,
            stats = stats,
            provider = this.provider.toApi(),
            latestStartDay = this.latestStartDay,
            matchedStartDay = this.state.matchedStartDay,
            matchedEndDay = this.state.matchedEndDay,
        )
}
