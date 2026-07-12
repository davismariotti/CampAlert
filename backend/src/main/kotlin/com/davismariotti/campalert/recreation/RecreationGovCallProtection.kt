package com.davismariotti.campalert.recreation

import com.newrelic.api.agent.NewRelic
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import io.github.resilience4j.retry.RetryRegistry
import org.springframework.stereotype.Component

/**
 * Shared "recreation-gov" call protection for every polling call site (campground availability,
 * zone/itinerary permit availability) — layers rate limiter (outermost, gates whether an attempt
 * starts) around the existing retry and circuit breaker, so a rate-limiter acquire timeout never
 * reaches (and never counts against) retry/circuit-breaker state. Not used by permit content/mapping
 * lookups (PermitContentCache, PermitClassificationService) — those are interactive, out of the
 * polling path, and keep using the circuit breaker/retry registries directly.
 */
@Component
class RecreationGovCallProtection(
    circuitBreakerRegistry: CircuitBreakerRegistry,
    retryRegistry: RetryRegistry,
    rateLimiterRegistry: RateLimiterRegistry,
) {
    private val rateLimiter = rateLimiterRegistry.rateLimiter("recreation-gov")
    private val retry = retryRegistry.retry("recreation-gov")
    private val circuitBreaker = circuitBreakerRegistry.circuitBreaker("recreation-gov")

    fun <T> execute(supplier: () -> T): T =
        try {
            rateLimiter.executeSupplier {
                retry.executeSupplier {
                    circuitBreaker.executeSupplier(supplier)
                }
            }
        } catch (e: RequestNotPermitted) {
            // Reflects our own offered load exceeding the rate limit, not a Recreation.gov-side
            // failure — tracked distinctly so it's never conflated with circuit breaker failures.
            NewRelic.getAgent().insights.recordCustomEvent("RecreationGovRateLimitTimeout", emptyMap<String, Any>())
            throw e
        }
}
