package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.recreation.PermitZoneAvailabilityCell
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class ZoneAvailabilityBaselineServiceTest {
    private val redisTemplate = mock(StringRedisTemplate::class.java)

    @Suppress("UNCHECKED_CAST")
    private val valueOps = mock(ValueOperations::class.java) as ValueOperations<String, String>
    private val objectMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    private val service = ZoneAvailabilityBaselineService(redisTemplate, objectMapper, 30L)

    private val month = YearMonth.of(2026, 7)
    private val date = ZonedDateTime.of(2026, 7, 13, 0, 0, 0, 0, ZoneOffset.UTC)
    private val key = "permit:zone-baseline:233261:343:2026-07"

    @BeforeEach
    fun setUp() {
        `when`(redisTemplate.opsForValue()).thenReturn(valueOps)
    }

    @Test
    fun `no prior baseline is never suspicious, and records this tick`() {
        `when`(valueOps.get(key)).thenReturn(null)

        val result = service.looksSuspicious("233261", "343", month, mapOf(date to PermitZoneAvailabilityCell(total = 25, remaining = 25)))

        assertFalse(result)
        verify(valueOps).set(eq(key), any(), eq(30L), eq(TimeUnit.MINUTES))
    }

    @Test
    fun `flags a date that flips from partially-booked to fully-open since the last tick`() {
        val previous = ZoneBaselineSnapshot(mapOf(date.toString() to PermitZoneAvailabilityCell(total = 25, remaining = 3)))
        `when`(valueOps.get(key)).thenReturn(objectMapper.writeValueAsString(previous))

        val result = service.looksSuspicious("233261", "343", month, mapOf(date to PermitZoneAvailabilityCell(total = 25, remaining = 25)))

        assertTrue(result)
    }

    @Test
    fun `does not flag a date that was already fully-open and stays fully-open`() {
        val previous = ZoneBaselineSnapshot(mapOf(date.toString() to PermitZoneAvailabilityCell(total = 25, remaining = 25)))
        `when`(valueOps.get(key)).thenReturn(objectMapper.writeValueAsString(previous))

        val result = service.looksSuspicious("233261", "343", month, mapOf(date to PermitZoneAvailabilityCell(total = 25, remaining = 25)))

        assertFalse(result)
    }

    @Test
    fun `does not flag a date with no corresponding entry in the previous baseline`() {
        val otherDate = ZonedDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        val previous = ZoneBaselineSnapshot(mapOf(otherDate.toString() to PermitZoneAvailabilityCell(total = 25, remaining = 3)))
        `when`(valueOps.get(key)).thenReturn(objectMapper.writeValueAsString(previous))

        val result = service.looksSuspicious("233261", "343", month, mapOf(date to PermitZoneAvailabilityCell(total = 25, remaining = 25)))

        assertFalse(result)
    }

    @Test
    fun `a partial-to-partial change is not flagged, only a jump straight to fully-open`() {
        val previous = ZoneBaselineSnapshot(mapOf(date.toString() to PermitZoneAvailabilityCell(total = 25, remaining = 3)))
        `when`(valueOps.get(key)).thenReturn(objectMapper.writeValueAsString(previous))

        val result = service.looksSuspicious("233261", "343", month, mapOf(date to PermitZoneAvailabilityCell(total = 25, remaining = 5)))

        assertFalse(result)
    }
}
