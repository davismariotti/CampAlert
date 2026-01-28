package com.davismariotti.campfinder.service

import com.davismariotti.campfinder.model.SearchRequest
import com.davismariotti.campfinder.recreation.Campground
import net.pushover.client.PushoverClient
import net.pushover.client.PushoverMessage
import net.pushover.client.PushoverRestClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class PushoverService(
    @Value("\${pushover.api_token}") private val apiToken: String,
    @Value("\${pushover.user_token}") private val userToken: String
) {

    fun pushMessage(request: SearchRequest, campground: Campground) {
        val client: PushoverClient = PushoverRestClient()

        client.pushMessage(
            PushoverMessage.Builder()
                .setApiToken(apiToken)
                .setUserId(userToken)
                .setMessage(buildMessage(campground, request))
                .setUrl(
                    "https://www.recreation.gov/camping/campgrounds/%d".format(request.campsiteId)
                )
                .build()
        )
    }

    fun buildMessage(campground: Campground, request: SearchRequest): String {
        val sb = StringBuilder()
        sb.append("${request.name} - ${request.startDay} (${request.startDay.plusDays(request.nights.toLong())})")
            .append("\n")

        // Construct the message parts for each campsite
        val messageParts = campground.campsites.values.map {
            "${it.loop} ${it.site} (${it.campsiteId})"
        }

        // Join the message parts with "\n" and append to the StringBuilder
        sb.append(messageParts.joinToString(separator = "\n"))

        return sb.toString()
    }

}