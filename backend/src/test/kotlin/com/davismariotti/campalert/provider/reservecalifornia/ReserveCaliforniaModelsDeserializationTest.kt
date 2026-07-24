package com.davismariotti.campalert.provider.reservecalifornia

import com.davismariotti.campalert.httpclient.baseProviderObjectMapper
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Deserializes real-shaped JSON through the actual configured ObjectMapper
 * (`ReserveCaliforniaConfiguration.getReserveCaliforniaClient`'s PascalCase naming strategy), unlike
 * every other ReserveCalifornia test, which mocks `Call.execute()` directly with a pre-built Kotlin
 * object and so never exercises real JSON parsing at all.
 *
 * This class exists because of a real production 404: `GET /api/campgrounds/407?provider=RESERVE_CALIFORNIA`
 * failed because ReserveCalifornia's grid response for facility 407's "Group Tent Campsite #KAYK" unit
 * sent an explicit JSON `"SleepingUnitIds": null` rather than `[]` — jackson-module-kotlin throws
 * `KotlinInvalidNullException` binding an explicit null to a non-nullable Kotlin collection property,
 * which `ReserveCaliforniaCatalogCache.fetchFacilityRoster`'s catch-and-log-null swallowed into a
 * silent empty roster. Every mocked-`Call` unit test in this suite would have kept passing regardless,
 * since none of them touch the real deserialization path this class targets directly.
 */
class ReserveCaliforniaModelsDeserializationTest {
    private val objectMapper: ObjectMapper = baseProviderObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE)

    @Test
    fun `a grid unit with an explicit null SleepingUnitIds and null Slices deserializes without throwing`() {
        // Real shape captured live from facility 407 (Angel Island SP, West Garrison) — a group/
        // non-standard unit type, unlike individual campsites which always had a populated array.
        val json = """
            {
              "Facility": {
                "FacilityId": 407,
                "Name": "West Garrison (sites 10 & Kayak)",
                "Latitude": 37.8613,
                "Longitude": -122.4407,
                "UnitCount": 1,
                "Units": {
                  "bucket3.39604": {
                    "UnitId": 39604,
                    "Name": "Group Tent Campsite #KAYK",
                    "IsFiltered": false,
                    "UnitCategoryId": 2,
                    "UnitTypeGroupId": 5,
                    "SleepingUnitIds": null,
                    "Slices": null
                  }
                }
              }
            }
        """.trimIndent()

        val response = objectMapper.readValue(json, ReserveCaliforniaGridResponse::class.java)

        val facility = response.facility!!
        assertEquals(407, facility.facilityId)
        val unit = facility.units!!.getValue("bucket3.39604")
        assertEquals(39604, unit.unitId)
        assertNull(unit.slices)
    }

    @Test
    fun `a filters response with an explicit null UnitTypesGroups deserializes without throwing`() {
        val json = """{"UnitTypesGroups": null}"""

        val response = objectMapper.readValue(json, ReserveCaliforniaFiltersResponse::class.java)

        assertNull(response.unitTypesGroups)
    }
}
