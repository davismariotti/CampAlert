package com.davismariotti.campalert.provider.globalentry

import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GlobalEntrySlotMatcherTest {
    private val day = LocalDate.of(2026, 10, 27)
    private val longAgo = LocalDateTime.of(2026, 1, 1, 0, 0)

    private fun slot(time: String, date: LocalDate = day): LocalDateTime = LocalDateTime.of(date, LocalTime.parse(time))

    private fun match(
        slots: List<LocalDateTime>,
        first: LocalDate = day,
        last: LocalDate = day,
        earliest: String? = null,
        latest: String? = null,
        now: LocalDateTime = longAgo,
    ) = GlobalEntrySlotMatcher.match(slots, first, last, earliest?.let(LocalTime::parse), latest?.let(LocalTime::parse), now)

    @Test
    fun `slot inside the window matches`() {
        assertNotNull(match(listOf(slot("11:40")), earliest = "11:30", latest = "14:00"))
    }

    @Test
    fun `slot before the window does not match`() {
        assertNull(match(listOf(slot("10:10")), earliest = "11:30", latest = "14:00"))
    }

    @Test
    fun `window start is inclusive`() {
        assertNotNull(match(listOf(slot("11:30")), earliest = "11:30", latest = "14:00"))
    }

    @Test
    fun `window end is exclusive`() {
        assertNull(match(listOf(slot("14:00")), earliest = "11:30", latest = "14:00"))
        assertNotNull(match(listOf(slot("13:50")), earliest = "11:30", latest = "14:00"))
    }

    @Test
    fun `no window means any slot on a requested day matches`() {
        assertNotNull(match(listOf(slot("07:30"))))
        assertNotNull(match(listOf(slot("21:20"))))
    }

    @Test
    fun `only an earliest bound leaves the end open`() {
        assertNotNull(match(listOf(slot("15:00")), earliest = "13:00"))
        assertNull(match(listOf(slot("12:50")), earliest = "13:00"))
    }

    @Test
    fun `only a latest bound leaves the start open`() {
        assertNotNull(match(listOf(slot("08:00")), latest = "09:00"))
        assertNull(match(listOf(slot("09:00")), latest = "09:00"))
    }

    @Test
    fun `slots outside the requested days do not match`() {
        assertNull(match(listOf(slot("12:00", day.plusDays(1))), first = day, last = day))
        assertNull(match(listOf(slot("12:00", day.minusDays(1))), first = day, last = day))
    }

    @Test
    fun `day range bounds are inclusive`() {
        val slots = listOf(slot("12:00", day), slot("12:00", day.plusDays(3)))
        assertEquals(2, match(slots, first = day, last = day.plusDays(3))!!.count)
    }

    @Test
    fun `slots that already started today do not match`() {
        val now = LocalDateTime.of(day, LocalTime.of(12, 55))
        assertNull(match(listOf(slot("08:50")), now = now))
        assertNotNull(match(listOf(slot("13:00")), now = now))
    }

    @Test
    fun `reports the count and the chronologically earliest match`() {
        val slots = listOf(slot("13:00", day.plusDays(1)), slot("12:10"), slot("11:40", day.plusDays(1)), slot("16:00"))
        val result = match(slots, last = day.plusDays(1))!!

        assertEquals(4, result.count)
        assertEquals(slot("12:10"), result.earliest)
    }

    @Test
    fun `an empty slot list does not match`() {
        assertNull(match(emptyList()))
    }
}
