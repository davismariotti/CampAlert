package com.davismariotti.campalert.recreation

import com.fasterxml.jackson.annotation.JsonProperty

data class RidbFacility(
    @JsonProperty("FacilityID") val facilityId: String,
    @JsonProperty("FacilityName") val facilityName: String,
    @JsonProperty("FacilityTypeDescription") val facilityTypeDescription: String?,
    @JsonProperty("ParentRecAreaID") val parentRecAreaId: Int?,
    @JsonProperty("FacilityLatitude") val facilityLatitude: Double?,
    @JsonProperty("FacilityLongitude") val facilityLongitude: Double?,
)
