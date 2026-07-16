package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.PermitsApiDelegate
import com.davismariotti.campalert.api.model.PermitAvailabilityCell
import com.davismariotti.campalert.api.model.PermitDivision
import com.davismariotti.campalert.api.model.PermitDivisionAvailabilityPreviewResponse
import com.davismariotti.campalert.api.model.PermitResponse
import com.davismariotti.campalert.api.model.PermitSearchResult
import com.davismariotti.campalert.api.model.PermitZoneAvailabilityPreviewResponse
import com.davismariotti.campalert.api.model.ProviderType
import com.davismariotti.campalert.exception.BadRequestException
import com.davismariotti.campalert.exception.UpstreamProviderException
import com.davismariotti.campalert.model.SearchType
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.provider.recreation.PermitRuleContent
import com.davismariotti.campalert.provider.recreation.PermitRuleName
import com.davismariotti.campalert.provider.recreation.RecreationApi
import com.davismariotti.campalert.provider.recreation.SearchEntityType
import com.davismariotti.campalert.service.permit.PermitClassificationException
import com.davismariotti.campalert.service.permit.PermitClassificationService
import com.davismariotti.campalert.service.permit.PermitContentCache
import com.davismariotti.campalert.util.naturalOrder
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
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
    override fun searchPermits(q: String, provider: ProviderType?): ResponseEntity<List<PermitSearchResult>> {
        if (q.isBlank()) {
            throw BadRequestException("Query parameter 'q' must not be blank")
        }
        val response = try {
            recreationCb.executeSupplier { recreationApi.searchSuggest(q).execute() }
        } catch (e: CallNotPermittedException) {
            log.warn("Recreation.gov circuit open for searchPermits q={}", q, e)
            return ResponseEntity.ok(emptyList())
        } catch (ex: Exception) {
            log.warn("Recreation.gov error for searchPermits q={}", q, ex)
            throw UpstreamProviderException()
        }
        if (!response.isSuccessful) {
            throw UpstreamProviderException()
        }
        // provider param is accepted for API-contract readiness but unused until a second provider
        // exists — every result is stamped RECREATION_GOV regardless (see design decision 7).
        val resultProvider = Provider.RECREATION_GOV.toApi()
        val results = response
            .body()
            ?.inventorySuggestions
            ?.filter { it.entityType == SearchEntityType.Permit }
            ?.map { suggestion ->
                val type = permitClassificationService.classify(suggestion.entityId)
                PermitSearchResult(
                    id = suggestion.entityId,
                    name = suggestion.name,
                    recareaName = suggestion.parentName,
                    type = type?.toApi(),
                    provider = resultProvider,
                )
            }
            ?: emptyList()
        return ResponseEntity.ok(results)
    }

    @PreAuthorize("isAuthenticated()")
    override fun getPermit(id: String): ResponseEntity<PermitResponse> {
        val type = permitClassificationService.classify(id)
            ?: throw PermitClassificationException.UnsupportedPermitType()

        val content = permitContentCache.get(id)
            ?: throw UpstreamProviderException()

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
        val type = permitClassificationService.classify(id) ?: throw PermitClassificationException.UnsupportedPermitType()
        if (type != SearchType.ZONE) {
            throw PermitClassificationException.TypeMismatch(type.toString())
        }

        val formattedStart = startDate
            .withDayOfMonth(1)
            .atStartOfDay()
            .atZone(ZoneOffset.UTC)
            .format(RecreationApi.dateFormatter)

        val response = try {
            recreationCb.executeSupplier { recreationApi.getZonePermitAvailability(id, formattedStart).execute() }
        } catch (e: CallNotPermittedException) {
            log.warn("Recreation.gov circuit open for getPermitAvailability id={}", id, e)
            throw UpstreamProviderException()
        } catch (ex: Exception) {
            log.warn("Recreation.gov error for getPermitAvailability id={}", id, ex)
            throw UpstreamProviderException()
        }
        if (!response.isSuccessful) {
            throw UpstreamProviderException()
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
        val type = permitClassificationService.classify(id) ?: throw PermitClassificationException.UnsupportedPermitType()
        if (type != SearchType.ITINERARY) {
            throw PermitClassificationException.TypeMismatch(type.toString())
        }

        val response = try {
            recreationCb.executeSupplier { recreationApi.getItineraryDivisionAvailability(id, divisionId, month, year).execute() }
        } catch (e: CallNotPermittedException) {
            log.warn("Recreation.gov circuit open for getPermitDivisionAvailability id={} divisionId={}", id, divisionId, e)
            throw UpstreamProviderException()
        } catch (ex: Exception) {
            log.warn("Recreation.gov error for getPermitDivisionAvailability id={} divisionId={}", id, divisionId, ex)
            throw UpstreamProviderException()
        }
        if (!response.isSuccessful) {
            throw UpstreamProviderException()
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

    private fun maxGroupSizeFor(rules: List<PermitRuleContent>, divisionId: String?): Int? =
        rules
            .firstOrNull { rule ->
                rule.name == PermitRuleName.MaxGroupSize &&
                    if (divisionId == null) rule.divisionId.isNullOrEmpty() else rule.divisionId == divisionId
            }?.value
            ?.toInt()
}
