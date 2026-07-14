package com.davismariotti.campalert.health

import org.junit.jupiter.api.Test
import org.springframework.boot.health.contributor.Status
import kotlin.test.assertEquals

class HttpHealthIndicatorTest {
    // Use an unreachable address to trigger a DOWN (connection refused)
    private val unreachableUrl = "http://127.0.0.1:1/"

    @Test
    fun `RecreationGov DOWN on connection error includes reason detail`() {
        val health = RecreationGovHealthIndicator(unreachableUrl).health()
        assertEquals(Status.DOWN, health.status)
        assertEquals(true, health.details.containsKey("reason"))
    }

    @Test
    fun `Ridb DOWN on connection error includes reason detail`() {
        val health = RidbHealthIndicator(unreachableUrl).health()
        assertEquals(Status.DOWN, health.status)
        assertEquals(true, health.details.containsKey("reason"))
    }

    @Test
    fun `CampLife DOWN on connection error includes reason detail`() {
        val health = CampLifeHealthIndicator(unreachableUrl).health()
        assertEquals(Status.DOWN, health.status)
        assertEquals(true, health.details.containsKey("reason"))
    }
}
