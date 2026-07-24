package com.davismariotti.campalert.provider.reservecalifornia

import com.davismariotti.campalert.provider.CallProtection
import com.davismariotti.campalert.service.redis.RedisJsonCache
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Wraps a cached value with when it was fetched, mirroring CampLifeCachedEntry. */
data class ReserveCaliforniaCachedEntry<T>(
    val fetchedAt: Instant,
    val value: T,
)

/**
 * Redis-backed cache for ReserveCalifornia's directory (Places+Facilities join, D2) and per-facility
 * roster (D7) — both cheap to rebuild, unlike the durable Postgres-backed occupancy pipeline
 * (`ReserveCaliforniaUnitOccupancyRepository`, section 6). Deliberately does not carry over
 * CampLifeCatalogCache's stale-read-triggered async refresh for the directory — the
 * `provider-catalog-caching` spec delta for ReserveCalifornia only specifies a scheduled weekly
 * refresh, not a staleness-triggered one.
 */
@Component
class ReserveCaliforniaCatalogCache(
    private val reserveCaliforniaApi: ReserveCaliforniaApi,
    private val redisJsonCache: RedisJsonCache,
    @Qualifier("reserveCaliforniaCallProtection") private val callProtection: CallProtection,
    private val properties: ReserveCaliforniaCatalogProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getDirectory(): List<ReserveCaliforniaDirectoryEntry> {
        val cached = redisJsonCache.get(DIRECTORY_KEY, directoryEntryTypeRef)
        if (cached != null) return cached.value
        return refreshDirectory() ?: emptyList()
    }

    /**
     * True if the scheduled refresh is actually due: the cache is empty (first start, or evicted
     * past its TTL with no read traffic to repopulate it) or the cached value is older than
     * [intervalMs] — mirrors CampLifeCatalogRefreshJob's own backstop-due check.
     */
    fun isDirectoryRefreshDue(intervalMs: Long): Boolean {
        val cached = redisJsonCache.get(DIRECTORY_KEY, directoryEntryTypeRef)
        return cached == null || Instant.now().toEpochMilli() - cached.fetchedAt.toEpochMilli() >= intervalMs
    }

    /** Fetches and joins ReserveCalifornia's park/facility lists, replacing the cached directory. On failure, the prior cached value (if any) is left untouched. */
    fun refreshDirectory(): List<ReserveCaliforniaDirectoryEntry>? =
        try {
            val places = callProtection.execute { reserveCaliforniaApi.getPlaces().execute().body() } ?: emptyList()
            val facilities = callProtection.execute { reserveCaliforniaApi.getFacilities().execute().body() } ?: emptyList()
            val placesById = places.associateBy { it.placeId }
            val entries = facilities.mapNotNull { facility ->
                val place = placesById[facility.placeId] ?: return@mapNotNull null
                ReserveCaliforniaDirectoryEntry(
                    facilityId = facility.facilityId,
                    facilityName = facility.name,
                    placeId = place.placeId,
                    placeName = place.name,
                    placeLatitude = place.latitude,
                    placeLongitude = place.longitude,
                )
            }
            redisJsonCache.set(DIRECTORY_KEY, ReserveCaliforniaCachedEntry(Instant.now(), entries), properties.ttlDays, TimeUnit.DAYS)
            entries
        } catch (e: Exception) {
            log.warn("Failed to refresh ReserveCalifornia directory cache", e)
            null
        }

    /**
     * Cache-aside per-facility roster (unit ids/names/categories/loop grouping, plus the facility's
     * own coordinates), populated on first miss via a grid call against a fixed near-term reference
     * window — not tied to any specific search's requested dates, since units are never omitted from
     * a grid response regardless of date range or availability (D7/D8).
     */
    fun getFacilityRoster(facilityId: Int): ReserveCaliforniaFacilityRoster? =
        redisJsonCache.getOrLoad(facilityRosterKey(facilityId), ReserveCaliforniaFacilityRoster::class.java, properties.ttlDays, TimeUnit.DAYS) {
            fetchFacilityRoster(facilityId)
        }

    /** Cache-aside per-park filter metadata (only `UnitTypesGroups` is consumed — D5's loop-name resolution). */
    fun getParkFilters(placeId: Int): ReserveCaliforniaFiltersResponse? =
        redisJsonCache.getOrLoad(parkFiltersKey(placeId), ReserveCaliforniaFiltersResponse::class.java, properties.ttlDays, TimeUnit.DAYS) {
            try {
                callProtection.execute { reserveCaliforniaApi.getFilters(placeId).execute().body() }
            } catch (e: Exception) {
                log.warn("Failed to fetch ReserveCalifornia park filters placeId={}", placeId, e)
                null
            }
        }

    private fun fetchFacilityRoster(facilityId: Int): ReserveCaliforniaFacilityRoster? =
        try {
            val today = LocalDate.now()
            val start = today.plusDays(1)
            val end = start.plusDays(7)
            // MinDate/MaxDate reflect ReserveCalifornia's real booking window per facility, which
            // isn't known before this call — verified live that a wide approximation here doesn't
            // affect which units/availability the response actually returns (the response's own
            // Facility.Restrictions carries the true window regardless of what was sent).
            val body = ReserveCaliforniaGridRequest(
                facilityId = facilityId.toString(),
                startDate = start.format(DATE_FORMATTER),
                endDate = end.format(DATE_FORMATTER),
                minDate = "${today.format(DATE_FORMATTER)}T00:00:00",
                maxDate = "${today.plusDays(180).format(DATE_FORMATTER)}T00:00:00",
            )
            val response = callProtection.execute { reserveCaliforniaApi.getGrid(body).execute().body() }
            val facility = response?.facility ?: return null
            ReserveCaliforniaFacilityRoster(
                facilityId = facility.facilityId,
                facilityName = facility.name,
                latitude = facility.latitude,
                longitude = facility.longitude,
                units = facility.units.orEmpty().values.map {
                    ReserveCaliforniaRosterUnit(
                        unitId = it.unitId,
                        name = it.name,
                        unitCategoryId = it.unitCategoryId,
                        unitTypeGroupId = it.unitTypeGroupId,
                    )
                },
            )
        } catch (e: Exception) {
            log.warn("Failed to fetch ReserveCalifornia facility roster facilityId={}", facilityId, e)
            null
        }

    companion object {
        private const val DIRECTORY_KEY = "reservecalifornia:catalog:directory"
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        private val directoryEntryTypeRef = object : tools.jackson.core.type.TypeReference<ReserveCaliforniaCachedEntry<List<ReserveCaliforniaDirectoryEntry>>>() {}

        private fun facilityRosterKey(facilityId: Int) = "reservecalifornia:catalog:facility:$facilityId"

        private fun parkFiltersKey(placeId: Int) = "reservecalifornia:catalog:filters:$placeId"
    }
}
