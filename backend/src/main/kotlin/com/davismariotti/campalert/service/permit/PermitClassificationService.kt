package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.model.SearchType
import com.davismariotti.campalert.recreation.PermitDivisionType
import com.davismariotti.campalert.recreation.PermitMappingPayload
import com.davismariotti.campalert.recreation.RecreationApi
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.TimeUnit

/**
 * Classifies a permit's reservation mechanism (design decision 1). `permitmapping` is the primary,
 * cheap, cacheable signal; an unflagged permit only gets accepted as [SearchType.ZONE] after a
 * structural fallback check against its own division/rule shape — anything else fails closed (null).
 */
@Service
class PermitClassificationService(
    private val recreationApi: RecreationApi,
    private val permitContentCache: PermitContentCache,
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val retryRegistry: RetryRegistry,
    @param:Value("\${campfinder.permit.mapping-cache-ttl-hours:24}")
    private val ttlHours: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val cb by lazy { circuitBreakerRegistry.circuitBreaker("recreation-gov") }
    private val retry by lazy { retryRegistry.retry("recreation-gov") }

    /** ZONE, ITINERARY, or unsupported (null) — never guesses; anything in doubt fails closed. */
    fun classify(permitId: String): SearchType? {
        val mapping = getMapping() ?: return null
        if (permitId in mapping.itineraryPermitIds) return SearchType.ITINERARY

        val unsupported = mapping.dayUsePermitIds + mapping.huntingPermitIds + mapping.landPermitIds +
            mapping.lotteryPermitIds + mapping.vehiclePermitIds + mapping.waterPermitIds + mapping.fakePermitIds
        if (permitId in unsupported) return null

        // Unflagged: verify structurally before trusting it as ZONE.
        val content = permitContentCache.get(permitId) ?: return null
        val hasDestinationZone = content.divisions.values.any { it.type == PermitDivisionType.DESTINATION_ZONE }
        val hasEnteringPerDayRule = content.rules.any { it.operation?.contains("EnteringPerDay") == true }
        return if (hasDestinationZone && hasEnteringPerDayRule) SearchType.ZONE else null
    }

    private fun getMapping(): PermitMappingPayload? {
        redisTemplate.opsForValue().get(MAPPING_CACHE_KEY)?.let {
            return objectMapper.readValue(it, PermitMappingPayload::class.java)
        }
        val fetched = fetchMapping() ?: return null
        redisTemplate.opsForValue().set(MAPPING_CACHE_KEY, objectMapper.writeValueAsString(fetched), ttlHours, TimeUnit.HOURS)
        return fetched
    }

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
            log.warn("Failed to fetch permitmapping", e)
            null
        }

    companion object {
        private const val MAPPING_CACHE_KEY = "permit:mapping"
    }
}
