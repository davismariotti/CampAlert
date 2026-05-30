package com.davismariotti.campfinder.delegate

import com.davismariotti.campfinder.api.CampgroundsApiDelegate
import com.davismariotti.campfinder.api.model.CampgroundResponse
import com.davismariotti.campfinder.api.model.CampsiteResponse
import com.davismariotti.campfinder.recreation.RecreationApi
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter

@Service
class CampgroundsDelegateImpl(
    private val recreationApi: RecreationApi
) : CampgroundsApiDelegate {

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
}
