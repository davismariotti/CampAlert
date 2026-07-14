package com.davismariotti.campalert.provider.recreation

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.ZonedDateTime

data class Campground(
    var campsites: Map<Int, Campsite>
)

data class Campsite(
    val campsiteId: Int,
    val site: String,
    val loop: String,
    val campsiteReserveType: String,
    var availabilities: Map<ZonedDateTime, AvailabilityType>,
    var quantities: Map<ZonedDateTime, Int>,
    @JsonProperty("min_num_people")
    val minimumNumberOfPeople: Int,
    @JsonProperty("max_num_people")
    val maximumNumberOfPeople: Int,
) {
    companion object {
        fun Campsite.mergeWith(other: Campsite): Campsite {
            val mergedAvailabilities = (this.availabilities + other.availabilities)

            val mergedQuantities = (this.quantities + other.quantities)

            return this.copy(
                availabilities = mergedAvailabilities,
                quantities = mergedQuantities
            )
        }

        fun Campground.mergeWith(other: Campground): Campground {
            val mergedCampsites = this.campsites.mapValues { (campsiteId, campsite) ->
                // Check if the other availability has the same campsiteId
                other.campsites[campsiteId]?.let { otherCampsite ->
                    // Merge the two campsites with matching IDs
                    campsite.mergeWith(otherCampsite)
                } ?: campsite // If no match, keep the original campsite
            }

            return this.copy(campsites = mergedCampsites)
        }

//        fun Map<ZonedDateTime, AvailabilityType>.filterByGroupSize(groupSize: Int): Map<ZonedDateTime, AvailabilityType> {
//            return this.filterValues { it. }
//        }
    }
}
