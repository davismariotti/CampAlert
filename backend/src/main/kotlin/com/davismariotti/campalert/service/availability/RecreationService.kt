package com.davismariotti.campalert.service.availability

import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.recreation.Campground
import java.time.YearMonth
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

interface RecreationService {
    fun checkAvailability(
        searchRequest: SearchRequest,
        user: User,
        campgroundCache: ConcurrentHashMap<Pair<Int, YearMonth>, CompletableFuture<Campground>>? = null,
    ): AvailabilityResult
}
