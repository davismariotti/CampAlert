package com.davismariotti.campalert.recreation

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate

/**
 * `GET permititinerary/{permitId}/division/{divisionId}/availability/month` — one call per
 * (division, month). Unlike the zone endpoint, date keys are plain `YYYY-MM-DD` (no time/zone
 * component) and the request takes `month`/`year` query params rather than `start_date` — confirmed
 * against live traffic, which differs from the trimmed appendix example.
 *
 * `quota_type_maps` can carry more than one map (e.g. `ConstantQuotaUsageDaily` and
 * `QuotaUsageByMemberDaily`), each representing a distinct quota Recreation.gov enforces
 * simultaneously — a date only truly has room when every present map shows `remaining > 0` for it.
 * The top-level `bools` field is intentionally not modeled: per the design's own live-verified notes
 * it folds in itinerary/cart context and isn't a plain availability signal.
 */
data class PermitItineraryAvailabilityResponse(
    @JsonProperty("payload") val payload: PermitItineraryAvailabilityPayload,
)

data class PermitItineraryAvailabilityPayload(
    @JsonProperty("quota_type_maps") val quotaTypeMaps: Map<String, Map<LocalDate, PermitItineraryAvailabilityCell>> = emptyMap(),
)

data class PermitItineraryAvailabilityCell(
    @JsonProperty("total") val total: Int = 0,
    @JsonProperty("remaining") val remaining: Int = 0,
    @JsonProperty("show_walkup") val showWalkup: Boolean = false,
    @JsonProperty("is_hidden") val isHidden: Boolean = false,
    @JsonProperty("season_type") val seasonType: String? = null,
)
