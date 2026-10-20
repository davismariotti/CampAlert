package com.davismariotti.campalert.provider.globalentry

import com.davismariotti.campalert.api.model.ProviderType
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GlobalEntryCatalogProviderTest {
    private val cache = mock(GlobalEntryCatalogCache::class.java)
    private val provider = GlobalEntryCatalogProvider(cache)

    private val directory = listOf(
        GlobalEntryLocation(5446, "SF Enrollment Center", "SF Enrollment Center", "San Francisco", "CA", "America/Los_Angeles"),
        GlobalEntryLocation(5140, "San Jose Enrollment Center", "San Jose", "San Jose", "CA", "America/Los_Angeles"),
        GlobalEntryLocation(5100, "Chicago O'Hare", "O'Hare", "Chicago", "IL", "America/Chicago"),
        GlobalEntryLocation(7000, "Doha Enrollment Center", "Doha", "Doha", "", "Asia/Qatar"),
    )

    init {
        `when`(cache.getDirectory()).thenReturn(directory)
        `when`(cache.findLocation(5446)).thenReturn(directory[0])
    }

    @Test
    fun `search by city matches case-insensitively and formats the display name`() {
        val results = provider.search("san fran")

        assertEquals(1, results.size)
        assertEquals(5446, results[0].id)
        assertEquals("SF Enrollment Center — San Francisco, CA", results[0].name)
        assertEquals(ProviderType.GLOBAL_ENTRY, results[0].provider.type)
        assertEquals("Global Entry", results[0].provider.name)
    }

    @Test
    fun `search by name matches`() {
        assertEquals(listOf(5100), provider.search("o'hare").map { it.id })
    }

    @Test
    fun `search by state code returns every center in that state`() {
        assertEquals(listOf(5446, 5140), provider.search("CA").map { it.id })
    }

    @Test
    fun `a short query is a state code lookup only, never a substring match`() {
        // "ca" is inside "Chicago" but Chicago is in IL.
        assertEquals(listOf(5446, 5140), provider.search("ca").map { it.id })
        assertEquals(listOf(5100), provider.search("IL").map { it.id })
        assertTrue(provider.search("c").isEmpty())
    }

    @Test
    fun `a three character query matches text as well as state`() {
        assertEquals(listOf(5100), provider.search("chi").map { it.id })
    }

    @Test
    fun `a blank query returns nothing`() {
        assertTrue(provider.search("   ").isEmpty())
    }

    @Test
    fun `a center without a state omits it from the display name`() {
        assertEquals("Doha Enrollment Center — Doha", provider.search("doha").single().name)
    }

    @Test
    fun `opts out of unscoped campground search`() {
        assertFalse(provider.includeInUnscopedSearch)
    }

    @Test
    fun `getCampground returns an empty campsite-shaped response for a known center`() {
        val response = provider.getCampground(5446)

        assertNotNull(response)
        assertTrue(response.campsites.isEmpty())
        assertNull(response.equipmentTypes)
        assertNull(response.amenities)
    }

    @Test
    fun `getCampground is null for an unknown center`() {
        assertNull(provider.getCampground(1))
    }

    @Test
    fun `getLoops is always empty`() {
        assertTrue(provider.getLoops(5446).isEmpty())
    }
}
