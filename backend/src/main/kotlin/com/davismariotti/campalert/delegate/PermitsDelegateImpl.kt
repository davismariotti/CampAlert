package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.PermitsApiDelegate
import com.davismariotti.campalert.api.model.ErrorResponse
import com.davismariotti.campalert.api.model.PermitAvailabilityCell
import com.davismariotti.campalert.api.model.PermitDivision
import com.davismariotti.campalert.api.model.PermitDivisionAvailabilityPreviewResponse
import com.davismariotti.campalert.api.model.PermitResponse
import com.davismariotti.campalert.api.model.PermitSearchResult
import com.davismariotti.campalert.api.model.PermitZoneAvailabilityPreviewResponse
import com.davismariotti.campalert.model.SearchType
import com.davismariotti.campalert.recreation.PermitRuleContent
import com.davismariotti.campalert.recreation.RecreationApi
import com.davismariotti.campalert.service.permit.PermitClassificationService
import com.davismariotti.campalert.service.permit.PermitContentCache
import com.davismariotti.campalert.util.naturalOrder
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.ZoneOffset

@Service
class PermitsDelegateImpl(
    private val recreationApi: RecreationApi,
    private val permitClassificationService: PermitClassificationService,
    private val permitContentCache: PermitContentCache,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
) : PermitsApiDelegate {
    private val log = LoggerFactory.getLogger(javaClass)
    private val recreationCb by lazy { circuitBreakerRegistry.circuitBreaker("recreation-gov") }

    @PreAuthorize("isAuthenticated()")
    override fun searchPermits(q: String): ResponseEntity<List<PermitSearchResult>> {
        if (q.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Query parameter 'q' must not be blank")
        }
        val response = try {
            recreationCb.executeSupplier { recreationApi.searchSuggest(q).execute() }
        } catch (e: CallNotPermittedException) {
            log.warn("Recreation.gov circuit open for searchPermits q={}", q)
            return ResponseEntity.ok(emptyList())
        } catch (ex: Exception) {
            log.warn("Recreation.gov error for searchPermits q={}", q, ex)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Recreation.gov upstream error")
        }
        if (!response.isSuccessful) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Recreation.gov upstream error")
        }
        val results = response
            .body()
            ?.inventorySuggestions
            ?.filter { it.entityType == "permit" }
            ?.map { suggestion ->
                val type = permitClassificationService.classify(suggestion.entityId)
                PermitSearchResult(
                    id = suggestion.entityId,
                    name = suggestion.name,
                    recareaName = suggestion.parentName,
                    type = type?.toApi(),
                )
            }
            ?: emptyList()
        return ResponseEntity.ok(results)
    }

    @Suppress("UNCHECKED_CAST")
    @PreAuthorize("isAuthenticated()")
    override fun getPermit(id: String): ResponseEntity<PermitResponse> {
        val type = permitClassificationService.classify(id)
            ?: return ResponseEntity.status(422).body(
                ErrorResponse(message = "Permit reservation mechanism is not supported", code = "PERMIT_TYPE_NOT_SUPPORTED"),
            ) as ResponseEntity<PermitResponse>

        val content = permitContentCache.get(id)
            ?: throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Recreation.gov upstream error")

        val divisions = content.divisions.values
            .sortedWith(compareBy(naturalOrder) { it.name ?: it.id })
            .map { division ->
                PermitDivision(
                    id = division.id,
                    name = division.name ?: division.id,
                    description = division.description,
                    district = division.district,
                    maxGroupSize = maxGroupSizeFor(content.rules, division.id),
                    childDivisionIds = if (type == SearchType.ITINERARY) division.children else null,
                )
            }

        return ResponseEntity.ok(
            PermitResponse(
                id = id,
                name = content.name ?: id,
                recareaName = content.recareaName,
                type = type.toApi(),
                maxGroupSize = maxGroupSizeFor(content.rules, null),
                divisions = divisions,
            ),
        )
    }

    @PreAuthorize("isAuthenticated()")
    override fun getPermitAvailability(id: String, startDate: LocalDate): ResponseEntity<PermitZoneAvailabilityPreviewResponse> {
        val type = permitClassificationService.classify(id) ?: return unsupportedPermitResponse()
        if (type != SearchType.ZONE) {
            return permitTypeMismatchResponse(type)
        }

        val formattedStart = startDate
            .withDayOfMonth(1)
            .atStartOfDay()
            .atZone(ZoneOffset.UTC)
            .format(RecreationApi.dateFormatter)

        val response = try {
            recreationCb.executeSupplier { recreationApi.getZonePermitAvailability(id, formattedStart).execute() }
        } catch (e: CallNotPermittedException) {
            log.warn("Recreation.gov circuit open for getPermitAvailability id={}", id)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Recreation.gov upstream error")
        } catch (ex: Exception) {
            log.warn("Recreation.gov error for getPermitAvailability id={}", id, ex)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Recreation.gov upstream error")
        }
        if (!response.isSuccessful) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Recreation.gov upstream error")
        }

        val divisions = response
            .body()
            ?.payload
            ?.availability
            ?.mapValues { (_, division) ->
                division.dateAvailability.entries.associate { (dateTime, cell) ->
                    dateTime.toLocalDate().toString() to PermitAvailabilityCell(total = cell.total, remaining = cell.remaining)
                }
            }
            ?: emptyMap()

        return ResponseEntity.ok(PermitZoneAvailabilityPreviewResponse(divisions = divisions))
    }

    @PreAuthorize("isAuthenticated()")
    override fun getPermitDivisionAvailability(
        id: String,
        divisionId: String,
        month: Int,
        year: Int,
    ): ResponseEntity<PermitDivisionAvailabilityPreviewResponse> {
        val type = permitClassificationService.classify(id) ?: return unsupportedPermitResponse()
        if (type != SearchType.ITINERARY) {
            return permitTypeMismatchResponse(type)
        }

        val response = try {
            recreationCb.executeSupplier { recreationApi.getItineraryDivisionAvailability(id, divisionId, month, year).execute() }
        } catch (e: CallNotPermittedException) {
            log.warn("Recreation.gov circuit open for getPermitDivisionAvailability id={} divisionId={}", id, divisionId)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Recreation.gov upstream error")
        } catch (ex: Exception) {
            log.warn("Recreation.gov error for getPermitDivisionAvailability id={} divisionId={}", id, divisionId, ex)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Recreation.gov upstream error")
        }
        if (!response.isSuccessful) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Recreation.gov upstream error")
        }

        // Collapse quota_type_maps the same way PermitAvailabilityMatcher.checkItinerary does: a date
        // only really has room when every concurrently-enforced quota type shows remaining > 0 for it.
        val quotaMaps = response.body()?.payload?.quotaTypeMaps ?: emptyMap()
        val dates = quotaMaps.values
            .flatMap { it.keys }
            .toSet()
            .associateWith { date ->
                PermitAvailabilityCell(
                    total = quotaMaps.values.minOf { it[date]?.total ?: 0 },
                    remaining = quotaMaps.values.minOf { it[date]?.remaining ?: 0 },
                )
            }.mapKeys { (date, _) -> date.toString() }

        return ResponseEntity.ok(PermitDivisionAvailabilityPreviewResponse(dates = dates))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> unsupportedPermitResponse(): ResponseEntity<T> =
        ResponseEntity.status(422).body(
            ErrorResponse(message = "Permit reservation mechanism is not supported", code = "PERMIT_TYPE_NOT_SUPPORTED"),
        ) as ResponseEntity<T>

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> permitTypeMismatchResponse(actual: SearchType): ResponseEntity<T> =
        ResponseEntity.status(422).body(
            ErrorResponse(message = "Permit is classified as $actual, not the type this endpoint requires", code = "PERMIT_TYPE_MISMATCH"),
        ) as ResponseEntity<T>

    private fun maxGroupSizeFor(rules: List<PermitRuleContent>, divisionId: String?): Int? =
        rules
            .firstOrNull { rule ->
                rule.name == "MaxGroupSize" &&
                    if (divisionId == null) rule.divisionId.isNullOrEmpty() else rule.divisionId == divisionId
            }?.value
            ?.toInt()
}
