package com.davismariotti.campalert.service.scheduling

import com.davismariotti.campalert.model.TargetType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PollTargetPhaseTest {
    @Test
    fun `phase offset is deterministic for the same target`() {
        val a = PollTargetPhase.phaseOffsetMs(TargetType.CAMPGROUND, "233359", 120000)
        val b = PollTargetPhase.phaseOffsetMs(TargetType.CAMPGROUND, "233359", 120000)
        assertEquals(a, b)
    }

    @Test
    fun `phase offset differs across different targets in general`() {
        val offsets = (1..50).map { PollTargetPhase.phaseOffsetMs(TargetType.CAMPGROUND, "campsite-$it", 120000) }
        assertTrue(offsets.distinct().size > 1, "expected more than one distinct offset across 50 targets")
    }

    @Test
    fun `phase offset is always within the interval bounds`() {
        repeat(200) { i ->
            val offset = PollTargetPhase.phaseOffsetMs(TargetType.PERMIT, "permit-$i", 120000)
            assertTrue(offset in 0 until 120000, "offset $offset out of range for target $i")
        }
    }

    @Test
    fun `target type is part of the hash input, not just target id`() {
        // Guards against a regression where the formula hashes only targetId and ignores targetType
        // (which would make every CAMPGROUND/PERMIT pair sharing an id collide). Across many shared
        // ids, at least one pair should land on a different offset if type is actually mixed in.
        val differsForAtLeastOne = (1..50).any { i ->
            PollTargetPhase.phaseOffsetMs(TargetType.CAMPGROUND, "$i", 120000) !=
                PollTargetPhase.phaseOffsetMs(TargetType.PERMIT, "$i", 120000)
        }
        assertTrue(differsForAtLeastOne)
    }

    @Test
    fun `rejects non-positive interval`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            PollTargetPhase.phaseOffsetMs(TargetType.CAMPGROUND, "1", 0)
        }
    }
}
