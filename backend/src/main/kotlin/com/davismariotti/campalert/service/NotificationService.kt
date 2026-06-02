package com.davismariotti.campalert.service

import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.recreation.Campground

interface NotificationService {
    fun notify(searchRequest: SearchRequest, campground: Campground, user: User,)
}
