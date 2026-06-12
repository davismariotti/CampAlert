package com.davismariotti.campalert.recreation

import com.fasterxml.jackson.annotation.JsonProperty

data class RidbFacilityResponse(
    @JsonProperty("RECDATA") val recdata: RidbFacility?
)
