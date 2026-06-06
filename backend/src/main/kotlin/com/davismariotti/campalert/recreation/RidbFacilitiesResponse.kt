package com.davismariotti.campalert.recreation

import com.fasterxml.jackson.annotation.JsonProperty

data class RidbFacilitiesResponse(
    @JsonProperty("RECDATA") val recdata: List<RidbFacility>?
)
