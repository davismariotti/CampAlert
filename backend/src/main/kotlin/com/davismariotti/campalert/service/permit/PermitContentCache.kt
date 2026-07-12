package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.recreation.PermitContentPayload
import com.davismariotti.campalert.recreation.RecreationApi
import com.davismariotti.campalert.service.redis.RedisJsonCache
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
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
    private val redisJsonCache: RedisJsonCache,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val retryRegistry: RetryRegistry,
    @param:Value("\${campfinder.permit.content-cache-ttl-hours:2}")
    private val ttlHours: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val cb by lazy { circuitBreakerRegistry.circuitBreaker("recreation-gov") }
    private val retry by lazy { retryRegistry.retry("recreation-gov") }

    /** Cached (or freshly fetched) divisions+rules for [permitId]; null when unreachable. */
    fun get(permitId: String): PermitContentPayload? = redisJsonCache.getOrLoad(cacheKey(permitId), PermitContentPayload::class.java, ttlHours, TimeUnit.HOURS) { fetch(permitId) }

    private fun fetch(permitId: String): PermitContentPayload? {
        val primary = try {
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
        } ?: return null

        if (primary.rules.isNotEmpty()) return primary

        // permitcontent/{id} returns an empty rules array for some permits (confirmed live for
        // Desolation, 233261) even though its divisions are populated correctly — permits/{id}/details
        // is the endpoint that actually carries rules for those permits. Without this fallback,
        // PermitClassificationService's structural check fails closed on data that was never fetched,
        // misclassifying every such zone permit as unsupported.
        val fallbackRules = try {
            retry.executeSupplier {
                cb.executeSupplier {
                    recreationApi
                        .getPermitDetails(permitId)
                        .execute()
                        .body()
                        ?.payload
                        ?.rules
                }
            }
        } catch (e: Exception) {
            log.warn("Failed to fetch permit details fallback rules permitId={}", permitId, e)
            null
        }

        return if (fallbackRules.isNullOrEmpty()) primary else primary.copy(rules = fallbackRules)
    }

    private fun cacheKey(permitId: String) = "permit:content:$permitId"
}
