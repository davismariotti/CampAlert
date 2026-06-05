package com.davismariotti.campalert.service.availability

import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.recreation.Campground

data class AvailabilityResult(
    val searchRequest: SearchRequest,
    val campground: Campground,
    val hasAvailableSites: Boolean,
)
