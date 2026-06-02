package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.CampgroundsApiDelegate
import com.davismariotti.campalert.api.model.CampgroundResponse
import com.davismariotti.campalert.api.model.CampgroundSearchResult
import com.davismariotti.campalert.api.model.CampsiteResponse
import com.davismariotti.campalert.recreation.RecreationApi
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.format.DateTimeFormatter

@Service
class CampgroundsDelegateImpl(
    private val recreationApi: RecreationApi
) : CampgroundsApiDelegate {
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
                    }
                )
            }
        )
        return ResponseEntity.ok(response)
    }

    @PreAuthorize("isAuthenticated()")
    override fun searchCampgrounds(q: String): ResponseEntity<List<CampgroundSearchResult>> {
        if (q.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Query parameter 'q' must not be blank")
        }
        val response = try {
            recreationApi.searchSuggest(q).execute()
        } catch (ex: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Recreation.gov upstream error")
        }
        if (!response.isSuccessful) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Recreation.gov upstream error")
        }
        val results = response.body()?.inventorySuggestions
            ?.filter { it.entityType == "campground" }
            ?.map { CampgroundSearchResult(id = it.entityId.toIntOrNull() ?: 0, name = it.name) }
            ?: emptyList()
        return ResponseEntity.ok(results)
    }
}
