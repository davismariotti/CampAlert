package com.davismariotti.campalert.provider.globalentry

import com.davismariotti.campalert.exception.BadRequestException
import org.junit.jupiter.api.Test
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GlobalEntryTimeWindowTest {
    @Test
    fun `parses a valid window`() {
        val window = GlobalEntryTimeWindow.parse("11:30", "14:00")

        assertEquals(LocalTime.of(11, 30), window.earliest)
        assertEquals(LocalTime.of(14, 0), window.latest)
    }

    @Test
    fun `either bound may be absent`() {
        assertEquals(LocalTime.of(9, 0), GlobalEntryTimeWindow.parse("09:00", null).earliest)
        assertNull(GlobalEntryTimeWindow.parse("09:00", null).latest)
        assertNull(GlobalEntryTimeWindow.parse(null, "17:00").earliest)
        assertTrue(GlobalEntryTimeWindow.parse(null, null).isEmpty)
    }

    @Test
    fun `an inverted window is rejected`() {
        assertFailsWith<BadRequestException> { GlobalEntryTimeWindow.parse("14:00", "11:30") }
    }

    @Test
    fun `equal bounds are rejected`() {
        assertFailsWith<BadRequestException> { GlobalEntryTimeWindow.parse("12:00", "12:00") }
    }

    @Test
    fun `malformed times are rejected`() {
        listOf("25:00", "noon", "9:00", "12:60", "12:00:30").forEach { bad ->
            assertFailsWith<BadRequestException>(bad) { GlobalEntryTimeWindow.parse(bad, null) }
        }
    }

    @Test
    fun `formats back to HH mm`() {
        assertEquals("09:05", GlobalEntryTimeWindow.format(LocalTime.of(9, 5)))
        assertNull(GlobalEntryTimeWindow.format(null))
    }
}
