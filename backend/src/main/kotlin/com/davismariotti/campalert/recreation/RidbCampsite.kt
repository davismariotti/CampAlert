package com.davismariotti.campalert.recreation

import com.fasterxml.jackson.annotation.JsonProperty

data class RidbCampsite(
    @JsonProperty("Loop") val loop: String?,
    @JsonProperty("CampsiteType") val campsiteType: String?
)

data class RidbCampsitesResponse(
    @JsonProperty("RECDATA") val recdata: List<RidbCampsite>?
)
