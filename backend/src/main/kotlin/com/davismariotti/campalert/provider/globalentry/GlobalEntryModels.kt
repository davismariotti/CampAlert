package com.davismariotti.campalert.provider.globalentry

import java.time.Instant

/**
 * One enrollment center from `GET locations/`. Only the fields CampAlert uses are mapped (the payload
 * also carries addresses, phone numbers, directions, etc.); `tzData` is an IANA zone id and is what a
 * Global Entry request's `campground_timezone` is set from, since the catalog has no coordinates.
 */
data class GlobalEntryLocation(
    val id: Int,
    val name: String,
    val shortName: String? = null,
    val city: String? = null,
    val state: String? = null,
    val tzData: String? = null,
    val remoteInd: Boolean = false,
)

/** The name shown in search results and stored as the request's `campground_name`: `"<name> — <city>, <state>"`, omitting blank parts (a few international centers have no state). */
fun GlobalEntryLocation.displayName(): String {
    val place = listOfNotNull(city, state).filter { it.isNotBlank() }.joinToString(", ")
    return if (place.isEmpty()) name else "$name — $place"
}

/** Wraps a cached value with when it was fetched, so staleness can be computed precisely on read rather than inferred from remaining Redis TTL. */
data class GlobalEntryCachedEntry<T>(
    val fetchedAt: Instant,
    val value: T,
)
