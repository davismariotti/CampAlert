package com.davismariotti.campalert.provider.reservecalifornia

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReserveCaliforniaCatalogProviderTest {
    private val catalogCache = mock(ReserveCaliforniaCatalogCache::class.java)
    private val provider = ReserveCaliforniaCatalogProvider(catalogCache)

    private val directory = listOf(
        ReserveCaliforniaDirectoryEntry(facilityId = 585, facilityName = "Live Oak Campground", placeId = 683, placeName = "Mount Diablo SP"),
        ReserveCaliforniaDirectoryEntry(facilityId = 591, facilityName = "Group Campsites", placeId = 683, placeName = "Mount Diablo SP"),
        ReserveCaliforniaDirectoryEntry(facilityId = 614, facilityName = "East Bay (sites 1-3)", placeId = 614, placeName = "Angel Island SP"),
    )

    @Test
    fun `matches on the facility's own name`() {
        `when`(catalogCache.getDirectory()).thenReturn(directory)

        val results = provider.search("live oak")

        assertEquals(listOf(585), results.map { it.id })
    }

    @Test
    fun `matches on the parent park name, surfacing every facility within it`() {
        `when`(catalogCache.getDirectory()).thenReturn(directory)

        val results = provider.search("diablo")

        assertEquals(setOf(585, 591), results.map { it.id }.toSet())
        assertTrue(results.all { it.name.contains("Mount Diablo SP") })
    }

    @Test
    fun `does not match an abbreviation not literally present in either name`() {
        `when`(catalogCache.getDirectory()).thenReturn(directory)

        val results = provider.search("mt diablo")

        assertEquals(emptyList(), results)
    }

    @Test
    fun `does not filter out group-camping facilities by type`() {
        `when`(catalogCache.getDirectory()).thenReturn(directory)

        val results = provider.search("group campsites")

        assertEquals(listOf(591), results.map { it.id })
    }
}
