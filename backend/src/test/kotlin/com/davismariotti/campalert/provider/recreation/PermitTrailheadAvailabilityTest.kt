package com.davismariotti.campalert.provider.recreation

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZonedDateTime

/** Deserialization of `permitinyo/.../availabilityv2` against real captured response shapes — see [PermitTrailheadAvailabilityCellDeserializer]. */
class PermitTrailheadAvailabilityTest {
    private val objectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    private fun parse(json: String): PermitTrailheadAvailabilityResponse = objectMapper.readValue(json, PermitTrailheadAvailabilityResponse::class.java)

    @Test
    fun `single-gate cell (Yosemite shape) parses one quota gate`() {
        val response = parse(
            """
            {"payload":{"2026-07-20":{"44585901":{"quota_usage_by_member_daily":{"total":30,"remaining":15},"is_walkup":false}}}}
            """.trimIndent(),
        )

        val cell = response.payload[LocalDate.of(2026, 7, 20)]!!["44585901"]!!
        assertEquals(1, cell.quotaGates.size)
        assertEquals(30, cell.quotaGates["quota_usage_by_member_daily"]!!.total)
        assertEquals(15, cell.quotaGates["quota_usage_by_member_daily"]!!.remaining)
        assertFalse(cell.isWalkup)
        assertFalse(cell.notYetReleased)
        assertNull(cell.releaseDate)
    }

    @Test
    fun `dual-gate cell (Enchantments shape) parses both disagreeing quota gates`() {
        val response = parse(
            """
            {"payload":{"2026-08-01":{"445863002":{"constant_quota_usage_daily":{"total":1,"remaining":1},"quota_usage_by_member_daily":{"total":8,"remaining":8},"is_walkup":false}}}}
            """.trimIndent(),
        )

        val cell = response.payload[LocalDate.of(2026, 8, 1)]!!["445863002"]!!
        assertEquals(2, cell.quotaGates.size)
        assertEquals(1, cell.quotaGates["constant_quota_usage_daily"]!!.remaining)
        assertEquals(8, cell.quotaGates["quota_usage_by_member_daily"]!!.remaining)
    }

    @Test
    fun `not_yet_released cell parses the flag and release date`() {
        val response = parse(
            """
            {"payload":{"2026-07-25":{"44585914":{"quota_usage_by_member_daily":{"total":15,"remaining":0},"is_walkup":true,"not_yet_released":true,"release_date":"2026-07-18T07:00:00-07:00"}}}}
            """.trimIndent(),
        )

        val cell = response.payload[LocalDate.of(2026, 7, 25)]!!["44585914"]!!
        assertTrue(cell.isWalkup)
        assertTrue(cell.notYetReleased)
        assertEquals(ZonedDateTime.parse("2026-07-18T07:00:00-07:00"), cell.releaseDate)
        assertEquals(0, cell.quotaGates["quota_usage_by_member_daily"]!!.remaining)
    }

    @Test
    fun `a division key absent from a date's response is simply absent from the map`() {
        val response = parse(
            """
            {"payload":{"2026-07-20":{"44585901":{"quota_usage_by_member_daily":{"total":30,"remaining":15},"is_walkup":false}},"2026-07-22":{}}}
            """.trimIndent(),
        )

        assertNull(response.payload[LocalDate.of(2026, 7, 22)]!!["44585901"])
        assertNull(response.payload[LocalDate.of(2026, 7, 21)])
    }
}
