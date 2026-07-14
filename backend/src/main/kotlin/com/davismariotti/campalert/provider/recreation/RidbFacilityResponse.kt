package com.davismariotti.campalert.provider.recreation

import com.fasterxml.jackson.annotation.JsonProperty

data class RidbFacilityResponse(
    @JsonProperty("RECDATA") val recdata: RidbFacility?
)
