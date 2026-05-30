package com.davismariotti.campalert.service

import com.davismariotti.campalert.model.SearchRequest

interface RecreationService {
    fun checkAvailability(searchRequest: SearchRequest)
}
