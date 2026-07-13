package com.davismariotti.campalert.camplife

import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component("campLife")
class CampLifeHealthIndicator(
    @Value("\${camplife.baseUrl}") private val baseUrl: String,
) : HealthIndicator {
    private val client = OkHttpClient
        .Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    override fun health(): Health =
        try {
            val response = client.newCall(Request.Builder().url(baseUrl).build()).execute()
            response.close()
            Health.up().build()
        } catch (e: Exception) {
            Health.down().withDetail("reason", e.message ?: "unknown error").build()
        }
}
