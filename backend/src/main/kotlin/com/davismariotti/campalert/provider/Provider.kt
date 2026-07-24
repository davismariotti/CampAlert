package com.davismariotti.campalert.provider

import com.davismariotti.campalert.model.SearchRequest

enum class Provider(
    val friendlyName: String
) {
    RECREATION_GOV("Recreation.gov") {
        override fun bookingLink(request: SearchRequest) = "recreation.gov/camping/campgrounds/${request.campsiteId}"
    },
    CAMPLIFE("CampLife") {
        override fun bookingLink(request: SearchRequest) = "https://www.camplife.com/${request.campsiteId}/reservation/step1"
    },
    RESERVE_CALIFORNIA("ReserveCalifornia") {
        // ReserveCalifornia's booking URL needs the parent park id, which a bare campsiteId can't
        // supply (design.md D11/D21) — placeId is always populated on reserveCaliforniaDetails.
        override fun bookingLink(request: SearchRequest) = "https://www.reservecalifornia.com/park/${request.reserveCaliforniaDetails?.placeId}/${request.campsiteId}"
    };

    /** The URL a user follows to book [request]'s campsite once it's available, shown in SMS alerts. */
    abstract fun bookingLink(request: SearchRequest): String

    /** Kebab-case key for external config/metrics, e.g. `RECREATION_GOV` -> "recreation-gov". */
    fun configName(): String = name.lowercase().replace('_', '-')
}
