package com.davismariotti.campalert.service.availability

import com.davismariotti.campalert.model.SearchRequest

data class AvailabilityResult(
    val searchRequest: SearchRequest,
    val hasAvailableSites: Boolean,
    val availableSiteCount: Int,
)
