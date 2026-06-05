package com.davismariotti.campalert.service.notification

import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.recreation.Campground
import net.pushover.client.PushoverClient
import net.pushover.client.PushoverMessage
import net.pushover.client.PushoverRestClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class PushoverService(
    @Value("\${pushover.api_token}") private val apiToken: String,
    @Value("\${pushover.user_token}") private val userToken: String,
) {
    fun pushMessage(request: SearchRequest, campground: Campground) {
        val client: PushoverClient = PushoverRestClient()
        client.pushMessage(
            PushoverMessage
                .Builder()
                .setApiToken(apiToken)
                .setUserId(userToken)
                .setMessage(buildMessage(campground, request))
                .setUrl("https://www.recreation.gov/camping/campgrounds/%d".format(request.campsiteId))
                .build(),
        )
    }

    fun buildMessage(campground: Campground, request: SearchRequest): String {
        val endDay = request.startDay.plusDays(request.nights.toLong())
        val sites = campground.campsites.values.joinToString("\n") { "${it.loop} ${it.site} (${it.campsiteId})" }
        return "${request.name} - ${request.startDay} ($endDay)\n$sites"
    }
}
