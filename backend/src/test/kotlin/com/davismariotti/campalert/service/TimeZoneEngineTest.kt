package com.davismariotti.campalert.service

import net.iakovlev.timeshape.TimeZoneEngine
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TimeZoneEngineTest {
    companion object {
        private lateinit var engine: TimeZoneEngine

        @BeforeAll
        @JvmStatic
        fun setup() {
            engine = TimeZoneEngine.initialize()
        }
    }

    @Test
    fun `Point Reyes resolves to America_Los_Angeles`() {
        val tz = engine.query(38.06, -122.86).orElse(null)
        assertEquals("America/Los_Angeles", tz?.id)
    }

    @Test
    fun `Yellowstone resolves to America_Denver`() {
        val tz = engine.query(44.43, -110.59).orElse(null)
        assertEquals("America/Denver", tz?.id)
    }

    @Test
    fun `Grand Canyon South Rim resolves to America_Phoenix`() {
        val tz = engine.query(36.06, -112.14).orElse(null)
        assertEquals("America/Phoenix", tz?.id)
    }
}
