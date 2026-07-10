package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.PermitsApiDelegate
import com.davismariotti.campalert.api.model.ErrorResponse
import com.davismariotti.campalert.api.model.PermitDivision
import com.davismariotti.campalert.api.model.PermitResponse
import com.davismariotti.campalert.api.model.PermitSearchResult
import com.davismariotti.campalert.model.SearchType
import com.davismariotti.campalert.recreation.RecreationApi
import com.davismariotti.campalert.service.permit.PermitClassificationService
import com.davismariotti.campalert.service.permit.PermitContentCache
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

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

        val divisions = content.divisions.values.map { division ->
            PermitDivision(
                id = division.id,
                name = division.name ?: division.id,
                description = division.description,
                childDivisionIds = if (type == SearchType.ITINERARY) division.children else null,
            )
        }

        return ResponseEntity.ok(
            PermitResponse(
                id = id,
                name = content.name ?: id,
                recareaName = content.recareaName,
                type = type.toApi(),
                divisions = divisions,
            ),
        )
    }
}
