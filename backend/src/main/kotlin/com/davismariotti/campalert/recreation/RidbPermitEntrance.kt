package com.davismariotti.campalert.recreation

import com.fasterxml.jackson.annotation.JsonProperty

data class RidbPermitEntrance(
    @JsonProperty("PermitEntranceID") val permitEntranceId: String,
    @JsonProperty("PermitEntranceName") val permitEntranceName: String?,
    @JsonProperty("PermitEntranceType") val permitEntranceType: String?,
    @JsonProperty("PermitEntranceDescription") val permitEntranceDescription: String?,
    @JsonProperty("District") val district: String?,
)

data class RidbPermitEntrancesResponse(
    @JsonProperty("RECDATA") val recdata: List<RidbPermitEntrance>?
)
