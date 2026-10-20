package com.davismariotti.campalert.provider.globalentry

import com.davismariotti.campalert.provider.CallProtection
import com.davismariotti.campalert.service.redis.RedisJsonCache
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Redis-backed cache of the Global Entry enrollment center directory — one key, refreshed on a
 * schedule with a read-triggered async refresh past a staleness threshold, same shape as the CampLife
 * directory. Remote (virtual) locations are dropped at fetch time so every consumer sees in-person
 * centers only. The key carries a version suffix: bump it whenever [GlobalEntryLocation] or the cached
 * wrapper changes shape, so a pre-deploy value is never read under the new type.
 */
@Component
class GlobalEntryCatalogCache(
    private val globalEntryApi: GlobalEntryApi,
    private val redisJsonCache: RedisJsonCache,
    @Qualifier("globalEntryCallProtection") private val callProtection: CallProtection,
    private val properties: GlobalEntryCatalogProperties,
    @Qualifier("globalEntryCatalogExecutor") private val executor: TaskExecutor,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Returns the cached directory, refreshing synchronously on a cold cache. When the cached value is
     * older than [GlobalEntryCatalogProperties.staleAfterDays] it's still returned immediately, but a
     * single-flight async refresh is kicked off in the background.
     */
    fun getDirectory(): List<GlobalEntryLocation> {
        val cached = redisJsonCache.get(DIRECTORY_KEY, directoryTypeRef)
        if (cached != null) {
            if (Duration.between(cached.fetchedAt, Instant.now()) > Duration.ofDays(properties.staleAfterDays)) {
                triggerAsyncDirectoryRefresh()
            }
            return cached.value
        }
        return refreshDirectory() ?: emptyList()
    }

    fun findLocation(id: Int): GlobalEntryLocation? = getDirectory().find { it.id == id }

    /** True if the scheduled backstop refresh is actually due: the cache is empty or older than [intervalMs]. */
    fun isDirectoryRefreshDue(intervalMs: Long): Boolean {
        val cached = redisJsonCache.get(DIRECTORY_KEY, directoryTypeRef)
        return cached == null || Duration.between(cached.fetchedAt, Instant.now()).toMillis() >= intervalMs
    }

    /** Fetches the directory and replaces the cached value. On failure, the prior cached value (if any) is left untouched. */
    fun refreshDirectory(): List<GlobalEntryLocation>? =
        try {
            val locations = (callProtection.execute { globalEntryApi.getLocations().execute().body() } ?: emptyList())
                .filterNot { it.remoteInd }
            redisJsonCache.set(DIRECTORY_KEY, GlobalEntryCachedEntry(Instant.now(), locations), properties.ttlDays, TimeUnit.DAYS)
            locations
        } catch (e: Exception) {
            log.warn("Failed to refresh Global Entry directory cache", e)
            null
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
        // Bump the version suffix whenever the cached value's shape changes (see class doc).
        internal const val DIRECTORY_KEY = "global-entry:catalog:directory:v1"
        private const val DIRECTORY_REFRESH_LOCK_KEY = "global-entry:catalog:directory:refresh-lock"
        private const val REFRESH_LOCK_SECONDS = 60L
        private val directoryTypeRef = object : tools.jackson.core.type.TypeReference<GlobalEntryCachedEntry<List<GlobalEntryLocation>>>() {}
    }
}
