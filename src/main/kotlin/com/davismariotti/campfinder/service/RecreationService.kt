package com.davismariotti.campfinder.service

import com.davismariotti.campfinder.model.SearchRequest

interface RecreationService {
    fun checkAvailability(searchRequest: SearchRequest)
}