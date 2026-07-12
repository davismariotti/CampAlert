package com.davismariotti.campalert.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CryptoUtilsTest {
    @Test
    fun `constantTimeEquals returns true for identical strings`() {
        assertTrue(CryptoUtils.constantTimeEquals("a".repeat(64), "a".repeat(64)))
    }

    @Test
    fun `constantTimeEquals returns false for differing strings of equal length`() {
        assertFalse(CryptoUtils.constantTimeEquals("a".repeat(64), "b".repeat(64)))
    }

    @Test
    fun `constantTimeEquals returns false for strings of different length`() {
        assertFalse(CryptoUtils.constantTimeEquals("a".repeat(64), "a".repeat(32)))
    }

    @Test
    fun `constantTimeEquals returns false when only a single trailing character differs`() {
        val a = "a".repeat(63) + "a"
        val b = "a".repeat(63) + "b"
        assertFalse(CryptoUtils.constantTimeEquals(a, b))
    }
}
