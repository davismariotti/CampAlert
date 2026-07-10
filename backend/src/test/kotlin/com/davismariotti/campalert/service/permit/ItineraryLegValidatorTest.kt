package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.recreation.PermitContentPayload
import com.davismariotti.campalert.recreation.PermitDivisionContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ItineraryLegValidatorTest {
    private fun division(id: String, children: List<String>) = PermitDivisionContent(id = id, children = children)

    private val content = PermitContentPayload(
        divisions = mapOf(
            "A" to division("A", children = listOf("B", "C")),
            "B" to division("B", children = listOf("D")),
            "C" to division("C", children = emptyList()),
            "D" to division("D", children = emptyList()),
        ),
    )

    @Test
    fun `first leg is unrestricted regardless of adjacency`() {
        val result = ItineraryLegValidator.validate(content, listOf("D"))
        assertTrue(result is LegValidationResult.Valid)
    }

    @Test
    fun `legal continuation is valid`() {
        val result = ItineraryLegValidator.validate(content, listOf("A", "B", "D"))
        assertTrue(result is LegValidationResult.Valid)
    }

    @Test
    fun `illegal continuation is invalid and identifies the offending leg`() {
        val result = ItineraryLegValidator.validate(content, listOf("A", "D"))
        assertTrue(result is LegValidationResult.Invalid)
        result as LegValidationResult.Invalid
        assertEquals(1, result.legIndex)
        assertEquals("D", result.divisionId)
    }

    @Test
    fun `unknown previous division is invalid`() {
        val result = ItineraryLegValidator.validate(content, listOf("Z", "A"))
        assertTrue(result is LegValidationResult.Invalid)
    }

    @Test
    fun `single leg itinerary is always valid`() {
        val result = ItineraryLegValidator.validate(content, listOf("Z"))
        assertTrue(result is LegValidationResult.Valid)
    }
}
