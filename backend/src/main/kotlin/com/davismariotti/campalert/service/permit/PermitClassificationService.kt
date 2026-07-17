package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.model.SearchType
import com.davismariotti.campalert.provider.recreation.PermitDivisionType
import com.davismariotti.campalert.provider.recreation.PermitMappingPayload
import com.davismariotti.campalert.provider.recreation.RecreationApi
import com.davismariotti.campalert.service.redis.RedisJsonCache
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * Classifies a permit's reservation mechanism (design decision 1). `permitmapping` is the primary,
 * cheap, cacheable signal; a permit not in the `itinerary`/`day_use`/`hunting`/`vehicle`/`water`/`fake`
 * buckets only gets accepted as [SearchType.ZONE] or [SearchType.TRAILHEAD] after a structural fallback
 * check against its own division/rule shape — anything else fails closed (null).
 *
 * `land`/`lottery` bucket membership is deliberately NOT in the unconditional-reject set: both were
 * confirmed to contain permits with a genuine `Entry Point` division structure (Yosemite, Mt. Whitney,
 * and several unrelated non-lottery forests), and the lottery flag is orthogonal to that shape — the
 * existing zone endpoint fails identically for lottery-flagged and non-lottery-flagged Entry Point
 * permits alike. Those buckets fall through to the same structural check an unflagged permit gets.
 */
@Service
class PermitClassificationService(
    private val recreationApi: RecreationApi,
    private val permitContentCache: PermitContentCache,
    private val redisJsonCache: RedisJsonCache,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val retryRegistry: RetryRegistry,
    @param:Value("\${campfinder.permit.mapping-cache-ttl-hours:24}")
    private val ttlHours: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val cb by lazy { circuitBreakerRegistry.circuitBreaker("recreation-gov") }
    private val retry by lazy { retryRegistry.retry("recreation-gov") }

    /** ZONE, ITINERARY, TRAILHEAD, or unsupported (null) — never guesses; anything in doubt fails closed. */
    fun classify(permitId: String): SearchType? {
        val mapping = getMapping() ?: return null
        if (permitId in mapping.itineraryPermitIds) return SearchType.ITINERARY

        val unsupported = mapping.dayUsePermitIds + mapping.huntingPermitIds +
            mapping.vehiclePermitIds + mapping.waterPermitIds + mapping.fakePermitIds
        if (permitId in unsupported) return null

        // Not itinerary-flagged or unconditionally unsupported: verify structurally before trusting it
        // as ZONE or TRAILHEAD. Applies equally to unflagged permits and to land/lottery-flagged ones.
        val content = permitContentCache.get(permitId) ?: return null
        val hasDestinationZone = content.divisions.values.any { it.type == PermitDivisionType.DESTINATION_ZONE }
        val hasEnteringPerDayRule = content.rules.any { it.operation?.contains("EnteringPerDay") == true }
        if (hasDestinationZone && hasEnteringPerDayRule) return SearchType.ZONE

        val hasEntryPoint = content.divisions.values.any { it.type == PermitDivisionType.ENTRY_POINT }
        if (hasEntryPoint) return SearchType.TRAILHEAD

        return null
    }

    private fun getMapping(): PermitMappingPayload? = redisJsonCache.getOrLoad(MAPPING_CACHE_KEY, PermitMappingPayload::class.java, ttlHours, TimeUnit.HOURS) { fetchMapping() }

    private fun fetchMapping(): PermitMappingPayload? =
        try {
            retry.executeSupplier {
                cb.executeSupplier {
                    recreationApi
                        .getPermitMapping()
                        .execute()
                        .body()
                        ?.payload
                }
            }
        } catch (e: Exception) {
            log.warn("Failed to fetch permit mapping", e)
            null
        }

    companion object {
        private const val MAPPING_CACHE_KEY = "permit:mapping"
    }
}
