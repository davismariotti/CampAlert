package com.davismariotti.campalert.provider.recreation

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.ZonedDateTime

/** `GET permits/{permitId}/divisions/{divisionId}/availability` — see [RecreationApi.getDivisionAvailability]. */
data class PermitDivisionAvailabilityResponse(
    @JsonProperty("payload") val payload: PermitDivisionAvailabilityPayload,
)

data class PermitDivisionAvailabilityPayload(
    @JsonProperty("permit_id") val permitId: String? = null,
    @JsonProperty("next_available_date") val nextAvailableDate: ZonedDateTime? = null,
    @JsonProperty("date_availability") val dateAvailability: Map<ZonedDateTime, PermitZoneAvailabilityCell> = emptyMap(),
)
