package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.CampgroundsApiDelegate
import com.davismariotti.campalert.api.model.CampgroundResponse
import com.davismariotti.campalert.api.model.CampgroundSearchResult
import com.davismariotti.campalert.api.model.LoopInfo
import com.davismariotti.campalert.api.model.ProviderType
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.service.availability.CampgroundCatalogProviderRegistry
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class CampgroundsDelegateImpl(
    private val campgroundCatalogProviderRegistry: CampgroundCatalogProviderRegistry,
) : CampgroundsApiDelegate {
    private val log = LoggerFactory.getLogger(javaClass)

    @PreAuthorize("isAuthenticated()")
    override fun getCampground(id: Int, provider: ProviderType?): ResponseEntity<CampgroundResponse> {
        val response = campgroundCatalogProviderRegistry.forProvider(provider?.toModel() ?: Provider.RECREATION_GOV).getCampground(id)
        return response?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()
    }

    @PreAuthorize("isAuthenticated()")
    override fun getCampgroundLoops(id: Int, provider: ProviderType?): ResponseEntity<List<LoopInfo>> {
        val loops = try {
            campgroundCatalogProviderRegistry.forProvider(provider?.toModel() ?: Provider.RECREATION_GOV).getLoops(id)
        } catch (ex: Exception) {
            log.warn("Catalog loops error provider={} id={}", provider, id, ex)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upstream error")
        }
        return ResponseEntity.ok(loops)
    }

    @PreAuthorize("isAuthenticated()")
    override fun searchCampgrounds(q: String, provider: ProviderType?): ResponseEntity<List<CampgroundSearchResult>> {
        if (q.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Query parameter 'q' must not be blank")
        }

        if (provider != null) {
            val results = try {
                campgroundCatalogProviderRegistry.forProvider(provider.toModel()).search(q)
            } catch (ex: Exception) {
                log.warn("Catalog search error provider={} q={}", provider, q, ex)
                throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upstream error")
            }
            return ResponseEntity.ok(results)
        }

        // Unscoped: merge every registered provider's results; one provider failing doesn't blank the others.
        val results = campgroundCatalogProviderRegistry.all().flatMap { searchProvider ->
            try {
                searchProvider.search(q)
            } catch (ex: Exception) {
                log.warn("Catalog search error provider={} q={}", searchProvider.provider, q, ex)
                emptyList()
            }
        }
        return ResponseEntity.ok(results)
    }
}
