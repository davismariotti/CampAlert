package com.davismariotti.campalert.service

import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.User

interface RecreationService {
    fun checkAvailability(searchRequest: SearchRequest, user: User,)
}
