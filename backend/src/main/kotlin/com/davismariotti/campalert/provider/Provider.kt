package com.davismariotti.campalert.provider

enum class Provider(
    val friendlyName: String
) {
    RECREATION_GOV("Recreation.gov") {
        override fun bookingLink(campsiteId: Int) = "recreation.gov/camping/campgrounds/$campsiteId"
    },
    CAMPLIFE("CampLife") {
        override fun bookingLink(campsiteId: Int) = "https://www.camplife.com/$campsiteId/reservation/step1"
    };

    /** The URL a user follows to book [campsiteId] once it's available, shown in SMS alerts. */
    abstract fun bookingLink(campsiteId: Int): String
}
