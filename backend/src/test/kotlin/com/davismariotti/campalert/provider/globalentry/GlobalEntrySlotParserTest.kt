package com.davismariotti.campalert.provider.globalentry

import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GlobalEntrySlotParserTest {
    private fun parse(json: String) = GlobalEntrySlotParser.parseBookable(json.byteInputStream())

    // Trimmed from a real captured response: pretty-printed, extra fields, mixed active values.
    private val captured = """
        [ {
          "timestamp" : "2026-10-26T07:30",
          "active" : 0,
          "conflicts" : 0,
          "duration" : 10,
          "pending" : 0,
          "remote" : false,
          "total" : 3
        }, {
          "timestamp" : "2026-10-27T10:10",
          "active" : 1,
          "conflicts" : 0,
          "duration" : 10,
          "pending" : 0,
          "remote" : false,
          "total" : 3
        }, {
          "timestamp" : "2026-10-27T10:20",
          "active" : 2,
          "conflicts" : 0,
          "duration" : 10,
          "pending" : 0,
          "remote" : false,
          "total" : 3
        } ]
    """.trimIndent()

    @Test
    fun `keeps only slots with active greater than zero`() {
        assertEquals(
            listOf(LocalDateTime.of(2026, 10, 27, 10, 10), LocalDateTime.of(2026, 10, 27, 10, 20)),
            parse(captured),
        )
    }

    @Test
    fun `tolerates field order and unknown fields including nested ones`() {
        val json = """[{"extra":{"a":[1,2,{"b":3}]},"active":1,"timestamp":"2026-10-27T10:10","other":"x"}]"""

        assertEquals(listOf(LocalDateTime.of(2026, 10, 27, 10, 10)), parse(json))
    }

    @Test
    fun `an empty array yields no slots`() {
        assertTrue(parse("[]").isEmpty())
    }

    @Test
    fun `a slot missing its timestamp is skipped`() {
        assertTrue(parse("""[{"active":1}]""").isEmpty())
    }

    @Test
    fun `a non-array body is reported as an IOException so the call protection records a failure`() {
        assertFailsWith<IOException> { parse("""{"error":"nope"}""") }
    }
}
