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
    fun `flags a date that flips from partially-booked to fully-open since the last confirmed tick`() {
        val previous = ZoneBaselineSnapshot(confirmed = mapOf(date.toString() to PermitZoneAvailabilityCell(total = 25, remaining = 3)))
        `when`(redisJsonCache.get(key, ZoneBaselineSnapshot::class.java)).thenReturn(previous)

        val result = service.looksSuspicious("233261", "343", month, mapOf(date to PermitZoneAvailabilityCell(total = 25, remaining = 25)))

        assertTrue(result)
    }

    @Test
    fun `does not flag a date that was already fully-open and stays fully-open`() {
        val open = mapOf(date.toString() to PermitZoneAvailabilityCell(total = 25, remaining = 25))
        val previous = ZoneBaselineSnapshot(confirmed = open, pending = open)
        `when`(redisJsonCache.get(key, ZoneBaselineSnapshot::class.java)).thenReturn(previous)

        val result = service.looksSuspicious("233261", "343", month, mapOf(date to PermitZoneAvailabilityCell(total = 25, remaining = 25)))

        assertFalse(result)
    }

    @Test
    fun `does not flag a date with no corresponding entry in the previous confirmed baseline`() {
        val otherDate = ZonedDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        val previous = ZoneBaselineSnapshot(confirmed = mapOf(otherDate.toString() to PermitZoneAvailabilityCell(total = 25, remaining = 3)))
        `when`(redisJsonCache.get(key, ZoneBaselineSnapshot::class.java)).thenReturn(previous)

        val result = service.looksSuspicious("233261", "343", month, mapOf(date to PermitZoneAvailabilityCell(total = 25, remaining = 25)))

        assertFalse(result)
    }

    @Test
    fun `a partial-to-partial change is not flagged, only a jump straight to fully-open`() {
        val previous = ZoneBaselineSnapshot(confirmed = mapOf(date.toString() to PermitZoneAvailabilityCell(total = 25, remaining = 3)))
        `when`(redisJsonCache.get(key, ZoneBaselineSnapshot::class.java)).thenReturn(previous)

        val result = service.looksSuspicious("233261", "343", month, mapOf(date to PermitZoneAvailabilityCell(total = 25, remaining = 5)))

        assertFalse(result)
    }

    @Test
    fun `a ghost reading that persists into the next tick is still flagged, not promoted to trusted after one repeat`() {
        val depleted = mapOf(date.toString() to PermitZoneAvailabilityCell(total = 25, remaining = 3))
        val full = mapOf(date.toString() to PermitZoneAvailabilityCell(total = 25, remaining = 25))
        // Tick N: still confirmed depleted, but a full reading was just seen and stashed as pending.
        val afterFirstFlip = ZoneBaselineSnapshot(confirmed = depleted, pending = full)
        `when`(redisJsonCache.get(key, ZoneBaselineSnapshot::class.java)).thenReturn(afterFirstFlip)

        // Tick N+1: the same full reading persists. This must still be flagged, because the confirmed
        // baseline is only promoted to `full` as a side effect of this call, not before it.
        val result = service.looksSuspicious("233261", "343", month, mapOf(date to PermitZoneAvailabilityCell(total = 25, remaining = 25)))

        assertTrue(result)
        verify(redisJsonCache).set(eq(key), eq(ZoneBaselineSnapshot(confirmed = full, pending = full)), eq(30L), eq(TimeUnit.MINUTES))
    }

    @Test
    fun `does not flag a jump to a different total, since that signals a legitimate quota-definition change`() {
        // Confirmed live: dates past a division's permit season report a sentinel total (e.g. 900000)
        // instead of the real quota — remaining goes from 3-of-25 to 900000-of-900000, which looks like
        // a flip but is really the season boundary, not the same 25-seat quota resetting to full.
        val previous = ZoneBaselineSnapshot(confirmed = mapOf(date.toString() to PermitZoneAvailabilityCell(total = 25, remaining = 3)))
        `when`(redisJsonCache.get(key, ZoneBaselineSnapshot::class.java)).thenReturn(previous)

        val result = service.looksSuspicious("233261", "343", month, mapOf(date to PermitZoneAvailabilityCell(total = 900000, remaining = 900000)))

        assertFalse(result)
    }

    @Test
    fun `a ghost reading that reverts before being confirmed is not promoted to trusted`() {
        val depleted = mapOf(date.toString() to PermitZoneAvailabilityCell(total = 25, remaining = 3))
        val full = mapOf(date.toString() to PermitZoneAvailabilityCell(total = 25, remaining = 25))
        val afterFirstFlip = ZoneBaselineSnapshot(confirmed = depleted, pending = full)
        `when`(redisJsonCache.get(key, ZoneBaselineSnapshot::class.java)).thenReturn(afterFirstFlip)

        service.looksSuspicious("233261", "343", month, mapOf(date to PermitZoneAvailabilityCell(total = 25, remaining = 3)))

        verify(redisJsonCache).set(eq(key), eq(ZoneBaselineSnapshot(confirmed = depleted, pending = depleted)), eq(30L), eq(TimeUnit.MINUTES))
    }
}
