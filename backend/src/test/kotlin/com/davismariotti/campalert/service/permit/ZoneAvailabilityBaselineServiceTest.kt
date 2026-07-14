package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.provider.recreation.PermitZoneAvailabilityCell
import com.davismariotti.campalert.service.redis.RedisJsonCache
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class ZoneAvailabilityBaselineServiceTest {
    private val redisJsonCache = mock(RedisJsonCache::class.java)

    private val service = ZoneAvailabilityBaselineService(redisJsonCache, 30L)

    private val month = YearMonth.of(2026, 7)
    private val date = ZonedDateTime.of(2026, 7, 13, 0, 0, 0, 0, ZoneOffset.UTC)
    private val key = "permit:zone-baseline:233261:343:2026-07"

    @Test
    fun `no prior baseline is never suspicious, and records this tick`() {
        `when`(redisJsonCache.get(key, ZoneBaselineSnapshot::class.java)).thenReturn(null)

        val result = service.looksSuspicious("233261", "343", month, mapOf(date to PermitZoneAvailabilityCell(total = 25, remaining = 25)))

        assertFalse(result)
        verify(redisJsonCache).set(eq(key), any(), eq(30L), eq(TimeUnit.MINUTES))
    }

    @Test
    fun `flags a date that flips from partially-booked to fully-open since the last tick`() {
        val previous = ZoneBaselineSnapshot(mapOf(date.toString() to PermitZoneAvailabilityCell(total = 25, remaining = 3)))
        `when`(redisJsonCache.get(key, ZoneBaselineSnapshot::class.java)).thenReturn(previous)

        val result = service.looksSuspicious("233261", "343", month, mapOf(date to PermitZoneAvailabilityCell(total = 25, remaining = 25)))

        assertTrue(result)
    }

    @Test
    fun `does not flag a date that was already fully-open and stays fully-open`() {
        val previous = ZoneBaselineSnapshot(mapOf(date.toString() to PermitZoneAvailabilityCell(total = 25, remaining = 25)))
        `when`(redisJsonCache.get(key, ZoneBaselineSnapshot::class.java)).thenReturn(previous)

        val result = service.looksSuspicious("233261", "343", month, mapOf(date to PermitZoneAvailabilityCell(total = 25, remaining = 25)))

        assertFalse(result)
    }

    @Test
    fun `does not flag a date with no corresponding entry in the previous baseline`() {
        val otherDate = ZonedDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        val previous = ZoneBaselineSnapshot(mapOf(otherDate.toString() to PermitZoneAvailabilityCell(total = 25, remaining = 3)))
        `when`(redisJsonCache.get(key, ZoneBaselineSnapshot::class.java)).thenReturn(previous)

        val result = service.looksSuspicious("233261", "343", month, mapOf(date to PermitZoneAvailabilityCell(total = 25, remaining = 25)))

        assertFalse(result)
    }

    @Test
    fun `a partial-to-partial change is not flagged, only a jump straight to fully-open`() {
        val previous = ZoneBaselineSnapshot(mapOf(date.toString() to PermitZoneAvailabilityCell(total = 25, remaining = 3)))
        `when`(redisJsonCache.get(key, ZoneBaselineSnapshot::class.java)).thenReturn(previous)

        val result = service.looksSuspicious("233261", "343", month, mapOf(date to PermitZoneAvailabilityCell(total = 25, remaining = 5)))

        assertFalse(result)
    }
}
