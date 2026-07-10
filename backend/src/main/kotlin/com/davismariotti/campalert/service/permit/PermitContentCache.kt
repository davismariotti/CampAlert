package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.recreation.PermitContentPayload
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
 * Redis-backed cache of a permit's divisions+rules, keyed by permit id, refreshed lazily on miss.
 * Shared by [PermitClassificationService]'s structural fallback check, the `GET /permits/{id}`
 * delegate, and [ItineraryLegValidator] call sites — one fetch per permit id backs all three
 * instead of each hitting Recreation.gov independently (design decision 6).
 */
@Service
class PermitContentCache(
    private val recreationApi: RecreationApi,
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val retryRegistry: RetryRegistry,
    @param:Value("\${campfinder.permit.content-cache-ttl-hours:2}")
    private val ttlHours: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val cb by lazy { circuitBreakerRegistry.circuitBreaker("recreation-gov") }
    private val retry by lazy { retryRegistry.retry("recreation-gov") }

    /** Cached (or freshly fetched) divisions+rules for [permitId]; null when unreachable. */
    fun get(permitId: String): PermitContentPayload? {
        val key = cacheKey(permitId)
        redisTemplate.opsForValue().get(key)?.let { return objectMapper.readValue(it, PermitContentPayload::class.java) }

        val fetched = fetch(permitId) ?: return null
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(fetched), ttlHours, TimeUnit.HOURS)
        return fetched
    }

    private fun fetch(permitId: String): PermitContentPayload? =
        try {
            retry.executeSupplier {
                cb.executeSupplier {
                    recreationApi
                        .getPermitContent(permitId)
                        .execute()
                        .body()
                        ?.payload
                }
            }
        } catch (e: Exception) {
            log.warn("Failed to fetch permit content permitId={}", permitId, e)
            null
        }

    private fun cacheKey(permitId: String) = "permit:content:$permitId"
}
