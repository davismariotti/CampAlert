package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.CampgroundsApiDelegate
import com.davismariotti.campalert.api.model.CampgroundResponse
import com.davismariotti.campalert.api.model.CampgroundSearchResult
import com.davismariotti.campalert.api.model.CampsiteResponse
import com.davismariotti.campalert.api.model.LoopInfo
import com.davismariotti.campalert.recreation.RecreationApi
import com.davismariotti.campalert.recreation.RidbApi
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.format.DateTimeFormatter

@Service
class CampgroundsDelegateImpl(
    private val recreationApi: RecreationApi,
    private val ridbApi: RidbApi,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
) : CampgroundsApiDelegate {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ridbCb by lazy { circuitBreakerRegistry.circuitBreaker("ridb") }

    @PreAuthorize("isAuthenticated()")
    override fun getCampground(id: Int): ResponseEntity<CampgroundResponse> {
        val campground = recreationApi.getCampgroundAvailability(id).execute().body()
            ?: return ResponseEntity.notFound().build()

        val response = CampgroundResponse(
            campsites = campground.campsites.entries.associate { (campsiteId, campsite) ->
                campsiteId.toString() to CampsiteResponse(
                    campsiteId = campsite.campsiteId,
                    site = campsite.site,
                    loop = campsite.loop,
                    campsiteReserveType = campsite.campsiteReserveType,
                    minimumNumberOfPeople = campsite.minimumNumberOfPeople,
                    maximumNumberOfPeople = campsite.maximumNumberOfPeople,
                    availabilities = campsite.availabilities.entries.associate { (dt, type) ->
                        dt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) to type.toValue()
                    },
                    quantities = campsite.quantities.entries.associate { (dt, qty) ->
                        dt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) to qty
                    },
                )
            },
        )
        return ResponseEntity.ok(response)
    }

    @PreAuthorize("isAuthenticated()")
    override fun getCampgroundLoops(id: Int): ResponseEntity<List<LoopInfo>> {
        val response = try {
            ridbCb.executeSupplier { ridbApi.getCampsites(id).execute() }
        } catch (e: CallNotPermittedException) {
            log.warn("RIDB circuit open for getCampsites id=$id")
            return ResponseEntity.ok(emptyList())
        } catch (ex: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "RIDB upstream error")
        }
        if (!response.isSuccessful) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "RIDB upstream error")
        }
        val bySites = response
            .body()
            ?.recdata
            ?.filter { !it.loop.isNullOrBlank() }
            ?.groupBy { it.loop!! }
            ?: emptyMap()
        val loops = bySites.entries
            .map { (loop, sites) ->
                LoopInfo(
                    name = loop,
                    boatInOnly = sites.all { it.campsiteType?.uppercase() == "BOAT IN" } ||
                        loop.uppercase().contains("BOAT"),
                )
            }.sortedBy { it.name }
        return ResponseEntity.ok(loops)
    }

    @PreAuthorize("isAuthenticated()")
    override fun searchCampgrounds(q: String): ResponseEntity<List<CampgroundSearchResult>> {
        if (q.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Query parameter 'q' must not be blank")
        }
        val response = try {
            ridbCb.executeSupplier { ridbApi.getFacilities(q).execute() }
        } catch (e: CallNotPermittedException) {
            log.warn("RIDB circuit open for searchCampgrounds q=$q")
            return ResponseEntity.ok(emptyList())
        } catch (ex: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "RIDB upstream error")
        }
        if (!response.isSuccessful) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "RIDB upstream error")
        }
        val results = response
            .body()
            ?.recdata
            ?.filter { it.facilityTypeDescription == "Campground" }
            ?.mapNotNull { facility ->
                facility.facilityId.toIntOrNull()?.let { id ->
                    CampgroundSearchResult(id = id, name = facility.facilityName)
                }
            }
            ?: emptyList()
        return ResponseEntity.ok(results)
    }
}
