package com.davismariotti.campalert.recreation

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.ZonedDateTime

/** `GET permits/{id}/availability/month` — one call covers every division on a zone permit. */
data class PermitZoneAvailabilityResponse(
    @JsonProperty("payload") val payload: PermitZoneAvailabilityPayload,
)

data class PermitZoneAvailabilityPayload(
    @JsonProperty("permit_id") val permitId: String? = null,
    /** Earliest date, across every division on the permit, that Recreation.gov currently considers bookable. */
    @JsonProperty("next_available_date") val nextAvailableDate: ZonedDateTime? = null,
    @JsonProperty("availability") val availability: Map<String, PermitZoneDivisionAvailability> = emptyMap(),
)

data class PermitZoneDivisionAvailability(
    @JsonProperty("division_id") val divisionId: String? = null,
    @JsonProperty("date_availability") val dateAvailability: Map<ZonedDateTime, PermitZoneAvailabilityCell> = emptyMap(),
)

data class PermitZoneAvailabilityCell(
    @JsonProperty("total") val total: Int = 0,
    @JsonProperty("remaining") val remaining: Int = 0,
    @JsonProperty("show_walkup") val showWalkup: Boolean = false,
    @JsonProperty("is_secret_quota") val isSecretQuota: Boolean = false,
)
