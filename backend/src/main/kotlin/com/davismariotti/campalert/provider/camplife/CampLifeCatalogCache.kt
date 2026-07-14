package com.davismariotti.campalert.provider.camplife

import com.davismariotti.campalert.provider.CallProtection
import com.davismariotti.campalert.service.redis.RedisJsonCache
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Wraps a cached value with when it was fetched, so staleness can be computed precisely on read rather than inferred from remaining Redis TTL. */
data class CampLifeCachedEntry<T>(
    val fetchedAt: Instant,
    val value: T,
)

/**
 * Redis-backed cache for CampLife's catalog data (design.md decisions 1-2): the global directory
 * (one key, refreshed on a schedule with a read-triggered async refresh past a staleness threshold)
 * and per-campground catalogs (cache-aside, populated on demand). There is deliberately no per-site
 * amenity cache tier — CampLife's per-site detail endpoint doesn't reliably indicate which sites
 * match a given amenity (see the availability endpoint's `cgAmenity`/`isFiltered` mechanism instead,
 * used directly by `CampLifeAvailabilityProvider`).
 */
@Component
class CampLifeCatalogCache(
    private val campLifeApi: CampLifeApi,
    private val redisJsonCache: RedisJsonCache,
    @Qualifier("campLifeCallProtection") private val callProtection: CallProtection,
    private val properties: CampLifeCatalogProperties,
    @Qualifier("campLifeCatalogExecutor") private val executor: TaskExecutor,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Returns the cached global directory, refreshing synchronously on a cold cache. When the cached
     * value is older than [CampLifeCatalogProperties.staleAfterDays], it's still returned immediately,
     * but a single-flight async refresh is kicked off in the background to catch the cache up.
     */
    fun getDirectory(): List<CampLifeDirectoryEntry> {
        val cached = redisJsonCache.get(DIRECTORY_KEY, directoryEntryTypeRef)
        if (cached != null) {
            if (Duration.between(cached.fetchedAt, Instant.now()) > Duration.ofDays(properties.staleAfterDays)) {
                triggerAsyncDirectoryRefresh()
            }
            return cached.value
        }
        return refreshDirectory() ?: emptyList()
    }

    /** Fetches the directory from CampLife and replaces the cached value. On failure, the prior cached value (if any) is left untouched. Called both synchronously (cold cache) and from the scheduled backstop job / async stale-read refresh. */
    fun refreshDirectory(): List<CampLifeDirectoryEntry>? =
        try {
            val entries = callProtection.execute { campLifeApi.getDirectory().execute().body() } ?: emptyList()
            redisJsonCache.set(DIRECTORY_KEY, CampLifeCachedEntry(Instant.now(), entries), properties.ttlDays, TimeUnit.DAYS)
            entries
        } catch (e: Exception) {
            log.warn("Failed to refresh CampLife directory cache", e)
            null
        }

    /** Cache-aside per-campground catalog (siteMap + grouping/equipment/amenity config), populated on first miss. */
    fun getCampgroundCatalog(campgroundId: Int): CampLifeSessionResponse? =
        redisJsonCache.getOrLoad(campgroundCatalogKey(campgroundId), CampLifeSessionResponse::class.java, properties.ttlDays, TimeUnit.DAYS) {
            try {
                callProtection.execute { campLifeApi.getCampgroundSession(campgroundId.toString()).execute().body() }
            } catch (e: Exception) {
                log.warn("Failed to fetch CampLife campground catalog campgroundId={}", campgroundId, e)
                null
            }
        }

    private fun triggerAsyncDirectoryRefresh() {
        executor.execute {
            // Single-flight guard: increment returns 1 only for the first caller within the lock's TTL window.
            val count = redisJsonCache.increment(DIRECTORY_REFRESH_LOCK_KEY, REFRESH_LOCK_SECONDS, TimeUnit.SECONDS)
            if (count == 1L) {
                refreshDirectory()
            }
        }
    }

    companion object {
        private const val DIRECTORY_KEY = "camplife:catalog:directory"
        private const val DIRECTORY_REFRESH_LOCK_KEY = "camplife:catalog:directory:refresh-lock"
        private const val REFRESH_LOCK_SECONDS = 60L
        private val directoryEntryTypeRef = object : tools.jackson.core.type.TypeReference<CampLifeCachedEntry<List<CampLifeDirectoryEntry>>>() {}

        private fun campgroundCatalogKey(campgroundId: Int) = "camplife:catalog:campground:$campgroundId"
    }
}
