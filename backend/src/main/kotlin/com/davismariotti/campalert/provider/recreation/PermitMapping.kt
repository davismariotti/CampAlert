package com.davismariotti.campalert.provider.recreation

import com.fasterxml.jackson.annotation.JsonProperty

data class PermitMappingResponse(
    @JsonProperty("payload") val payload: PermitMappingPayload,
)

/** Global (not permit-specific) classification of every permit id Recreation.gov knows about. */
data class PermitMappingPayload(
    @JsonProperty("day_use_permit_ids") val dayUsePermitIds: List<String> = emptyList(),
    @JsonProperty("early_access_permit_ids") val earlyAccessPermitIds: List<String> = emptyList(),
    @JsonProperty("fake_permit_ids") val fakePermitIds: List<String> = emptyList(),
    @JsonProperty("hunting_permit_ids") val huntingPermitIds: List<String> = emptyList(),
    @JsonProperty("itinerary_permit_ids") val itineraryPermitIds: List<String> = emptyList(),
    @JsonProperty("land_permit_ids") val landPermitIds: List<String> = emptyList(),
    @JsonProperty("lottery_permit_ids") val lotteryPermitIds: List<String> = emptyList(),
    @JsonProperty("vehicle_permit_ids") val vehiclePermitIds: List<String> = emptyList(),
    @JsonProperty("water_permit_ids") val waterPermitIds: List<String> = emptyList(),
)
