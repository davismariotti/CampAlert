package com.davismariotti.campalert.provider.recreation

import com.fasterxml.jackson.annotation.JsonProperty

data class RidbFacilitiesResponse(
    @JsonProperty("RECDATA") val recdata: List<RidbFacility>?
)
