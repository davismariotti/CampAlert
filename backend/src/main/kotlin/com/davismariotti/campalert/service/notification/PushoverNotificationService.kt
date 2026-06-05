package com.davismariotti.campalert.service.notification

import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.recreation.Campground
import net.pushover.client.PushoverMessage
import net.pushover.client.PushoverRestClient

class PushoverNotificationService(
    private val apiToken: String,
    private val userKey: String,
) : NotificationService {
    override fun notify(searchRequest: SearchRequest, campground: Campground, user: User) {
        val client = PushoverRestClient()
        client.pushMessage(
            PushoverMessage
                .Builder()
                .setApiToken(apiToken)
                .setUserId(userKey)
                .setMessage(buildMessage(campground, searchRequest))
                .setUrl("https://www.recreation.gov/camping/campgrounds/${searchRequest.campsiteId}")
                .build(),
        )
    }

    private fun buildMessage(campground: Campground, request: SearchRequest): String {
        val endDay = request.startDay.plusDays(request.nights.toLong())
        val sites = campground.campsites.values.joinToString("\n") { "${it.loop} ${it.site} (${it.campsiteId})" }
        return "${request.name} - ${request.startDay} ($endDay)\n$sites"
    }
}
