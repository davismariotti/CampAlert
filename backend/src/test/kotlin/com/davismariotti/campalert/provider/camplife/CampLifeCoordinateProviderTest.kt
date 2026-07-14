package com.davismariotti.campalert.provider.camplife

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CampLifeCoordinateProviderTest {
    private val campLifeCatalogCache = mock(CampLifeCatalogCache::class.java)
    private val provider = CampLifeCoordinateProvider(campLifeCatalogCache)

    @Test
    fun `resolves lat lon from the cached directory entry`() {
        `when`(campLifeCatalogCache.getDirectory()).thenReturn(
            listOf(CampLifeDirectoryEntry(id = 791, name = "Collins Lake", lat = "39.3374", lon = "-121.1544")),
        )

        assertEquals(39.3374 to -121.1544, provider.resolveCoordinates(791))
    }

    @Test
    fun `returns null coordinates when no directory entry matches`() {
        `when`(campLifeCatalogCache.getDirectory()).thenReturn(emptyList())

        val (lat, lon) = provider.resolveCoordinates(791)

        assertNull(lat)
        assertNull(lon)
    }
}
