package com.davismariotti.campalert.camplife

/*
 * All shapes in this file are verified against real captured traffic (HAR captures against
 * www.camplife.com, campground id 791 "Collins Lake") rather than guessed from documentation —
 * CampLife has none. Notably: `lat`/`lon` are JSON strings, not numbers; `siteTypes`/`equipTypes` are
 * objects with `id`/`name` (not bare strings); a site's grouping name is directly available as
 * `typeName` (no id-lookup needed); there is no per-site minimum-occupancy field; the availability
 * response's `sites` are `{id, isFiltered}` objects, not bare IDs; and — critically — `isFiltered`
 * means the site was EXCLUDED by the requested `cgAmenity` filter, not that it matched (verified by
 * cross-referencing 14 sample sites against their true per-site amenity ids). `cgAmenity` (not the
 * similarly-named `amenities` field, which was verified inert) is what actually drives filtering, and
 * multiple ids in `cgAmenity` combine with AND semantics (a site must have all of them to avoid being
 * filtered out).
 */

/** One entry from CampLife's global directory (`GET /api/campgrounds?all=true&minimal=true`). */
data class CampLifeDirectoryEntry(
    val id: Int = 0,
    val name: String = "",
    val city: String? = null,
    val stateProvince: String? = null,
    val lat: String? = null,
    val lon: String? = null,
)

/** `GET /api/reservation/session?campgroundIdOrAlias={id}` — a campground's site catalog. */
data class CampLifeSessionResponse(
    val siteMap: Map<String, CampLifeSite> = emptyMap(),
    val config: CampLifeCatalogConfig = CampLifeCatalogConfig(),
)

data class CampLifeSite(
    val id: Int = 0,
    val name: String = "",
    val typeName: String? = null,
    val maxOccupants: Int? = null,
)

data class CampLifeCatalogConfig(
    val siteTypes: List<CampLifeSiteType> = emptyList(),
    val equipTypes: List<CampLifeEquipType> = emptyList(),
    val amenities: List<CampLifeAmenity> = emptyList(),
)

/** A grouping (`typeName` on [CampLifeSite]) plus which equipment/amenity ids are generally associated with it. */
data class CampLifeSiteType(
    val id: Int = 0,
    val name: String = "",
    val equipTypeIds: List<Int> = emptyList(),
    val amenityIds: List<Int> = emptyList(),
)

data class CampLifeEquipType(
    val id: Int = 0,
    val name: String = "",
)

data class CampLifeAmenity(
    val id: Int = 0,
    val name: String = "",
)

data class CampLifeAvailabilityRequest(
    val checkinDate: String,
    val checkoutDate: String,
    val flexible: Boolean = false,
    val displayStartDate: String = "",
    val cgAmenity: List<Int> = emptyList(),
    val siteTypeId: Int? = null,
    val amenities: List<Int> = emptyList(),
)

data class CampLifeAvailableSite(
    val id: Int = 0,
    val isFiltered: Boolean = false,
)

/**
 * `POST /api/campground/{id}/availability` response. `sites` carries available sites on success;
 * `errors.general` accompanies HTTP 400 (e.g. same-day booking rejected); `warnings.general`
 * accompanies HTTP 200 with an empty `sites` array (e.g. below a site's minimum-stay requirement).
 */
data class CampLifeAvailabilityResponse(
    val sites: List<CampLifeAvailableSite>? = null,
    val errors: CampLifeMessages? = null,
    val warnings: CampLifeMessages? = null,
)

data class CampLifeMessages(
    val general: List<CampLifeMessage>? = null,
)

data class CampLifeMessage(
    val message: String = "",
)
