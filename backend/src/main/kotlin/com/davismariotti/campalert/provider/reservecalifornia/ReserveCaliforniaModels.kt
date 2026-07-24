package com.davismariotti.campalert.provider.reservecalifornia

/*
 * All shapes here are verified against real ReserveCalifornia API traffic — see design.md's
 * Appendix in the add-reserve-california-provider change for the literal JSON these are modeled
 * from. ReserveCalifornia's JSON uses PascalCase keys (`FacilityId`, `IsFiltered`, `MaxOccupancy`);
 * these classes use ordinary camelCase Kotlin property names and rely on the
 * PropertyNamingStrategies.UPPER_CAMEL_CASE-configured ObjectMapper built in
 * ReserveCaliforniaConfiguration to map between the two, rather than annotating every field.
 * Only fields actually consumed by this provider are modeled — FAIL_ON_UNKNOWN_PROPERTIES is off,
 * so anything else present in a real response is silently ignored.
 */

/** `GET /rdr/fd/places` — one park. */
data class ReserveCaliforniaPlace(
    val placeId: Int = 0,
    val name: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val city: String? = null,
    val state: String? = null,
)

/** `GET /rdr/fd/facilities` — one facility ("campground" per design.md D1), linked to its parent park via [placeId]. No coordinates here — see D4. */
data class ReserveCaliforniaFacility(
    val facilityId: Int = 0,
    val placeId: Int = 0,
    val name: String = "",
)

/**
 * `GET /rdr/search/filters/{placeId}` — only `UnitTypesGroups` (the loop-equivalent grouping, D5) is
 * consumed. Nullable, not defaulted to an empty list: verified live that ReserveCalifornia sends an
 * explicit JSON `null` for some array fields on some responses (see [ReserveCaliforniaGridUnit]'s own
 * note) rather than always `[]` — jackson-module-kotlin throws `KotlinInvalidNullException` binding an
 * explicit null to a non-nullable Kotlin collection property, so every field deserialized directly
 * from this API's real responses must be nullable regardless of whether a sane default "should" apply.
 */
data class ReserveCaliforniaFiltersResponse(
    val unitTypesGroups: List<ReserveCaliforniaUnitTypesGroup>? = null,
)

data class ReserveCaliforniaUnitTypesGroup(
    val unitCategoryId: Int = 0,
    val unitTypesGroupId: Int = 0,
    val unitTypesGroupName: String = "",
)

/** `POST /rdr/search/grid` request body. `facilityId` is deliberately a String — the real API expects one despite the id being numeric. */
data class ReserveCaliforniaGridRequest(
    val facilityId: String,
    val unitSort: String = "availability",
    val startDate: String,
    val endDate: String,
    val inSeasonOnly: Boolean = true,
    val webOnly: Boolean = true,
    val maxDate: String,
    val minDate: String,
    val isADA: Boolean = false,
    val restrictADA: Boolean = false,
    val unitCategoryId: Int = 0,
    val sleepingUnitId: Int = 0,
    val minVehicleLength: Int = 0,
    val unitTypesGroupIds: List<Int> = emptyList(),
    val amenityIds: List<Int> = emptyList(),
    val customerId: Int = 0,
    val customerClassificationId: Int = 0,
)

/** `POST /rdr/search/grid` response — both the site roster and per-day availability come from this one call (design.md D7). */
data class ReserveCaliforniaGridResponse(
    val facility: ReserveCaliforniaGridFacility? = null,
)

data class ReserveCaliforniaGridFacility(
    val facilityId: Int = 0,
    val name: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val unitCount: Int? = null,
    val availableUnitCount: Int? = null,
    /** Nullable, not defaulted — see [ReserveCaliforniaGridUnit]'s note on why every collection field deserialized from this API must be. */
    val units: Map<String, ReserveCaliforniaGridUnit>? = null,
)

/**
 * One unit within a facility's grid response. [isFiltered] is the server-side resolution of any
 * unitCategoryId/sleepingUnitId/minVehicleLength/amenityIds filters sent in the request — verified
 * to never be omitted from the response for failing a filter or being fully unavailable (D8); a
 * candidate only matches when `isFiltered == false`. No occupancy field exists on this object —
 * that's only available via [ReserveCaliforniaApi.getUnitDetails] (design.md D9).
 *
 * Deliberately carries no `sleepingUnitIds` field (unused, and it's exactly what caused a real 404:
 * a group/non-standard unit — e.g. "Group Tent Campsite #KAYK" at Angel Island facility 407 —
 * returned `"SleepingUnitIds": null` rather than `[]`, and jackson-module-kotlin throws
 * `KotlinInvalidNullException` binding an explicit JSON null to a non-nullable Kotlin collection
 * property, which `fetchFacilityRoster`'s catch-and-log-null swallowed into a silent empty roster and
 * a 404. [slices] is nullable for the same reason, verified live to matter for this exact unit type.
 */
data class ReserveCaliforniaGridUnit(
    val unitId: Int = 0,
    val name: String = "",
    val shortName: String? = null,
    val isFiltered: Boolean = false,
    val unitCategoryId: Int? = null,
    val unitTypeGroupId: Int? = null,
    val unitTypeId: Int? = null,
    val availableCount: Int? = null,
    val slices: Map<String, ReserveCaliforniaSlice>? = null,
)

data class ReserveCaliforniaSlice(
    val date: String = "",
    val isFree: Boolean = false,
    val isBlocked: Boolean = false,
)

/** `GET /rdr/search/details/{unitId}/startdate/{date}/nights/{nights}/0/0` — the only source of per-unit occupancy (design.md D9). */
data class ReserveCaliforniaDetailsResponse(
    val nightlyUnit: ReserveCaliforniaNightlyUnit? = null,
)

data class ReserveCaliforniaNightlyUnit(
    val unitId: Int = 0,
    val maxOccupancy: Int? = null,
    val minOccupancy: Int? = null,
)

/**
 * One entry in the cached, locally-built directory (design.md D2/D6) — a join of a
 * [ReserveCaliforniaFacility] and its parent [ReserveCaliforniaPlace], since ReserveCalifornia's own
 * live search only matches park names (Appendix §1), not facility names.
 */
data class ReserveCaliforniaDirectoryEntry(
    val facilityId: Int = 0,
    val facilityName: String = "",
    val placeId: Int = 0,
    val placeName: String = "",
    val placeLatitude: Double? = null,
    val placeLongitude: Double? = null,
)

/**
 * A facility's cached unit roster (design.md D6/D7) — built from a `POST /rdr/search/grid` call
 * against a fixed near-term reference window, not any specific search's actual requested dates
 * (units are never omitted from a grid response regardless of the date range or availability, so any
 * near-term window yields the complete roster). Also carries the facility's own coordinates,
 * captured as a free byproduct of that same call (D4).
 */
data class ReserveCaliforniaFacilityRoster(
    val facilityId: Int = 0,
    val facilityName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val units: List<ReserveCaliforniaRosterUnit> = emptyList(),
)

data class ReserveCaliforniaRosterUnit(
    val unitId: Int = 0,
    val name: String = "",
    val unitCategoryId: Int? = null,
    val unitTypeGroupId: Int? = null,
)
