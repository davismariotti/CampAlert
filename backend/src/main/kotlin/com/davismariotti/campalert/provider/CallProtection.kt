package com.davismariotti.campalert.provider

import com.newrelic.api.agent.NewRelic
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryRegistry

/**
 * A provider's own call protection: circuit breaker + retry, with an optional rate limiter
 * (outermost, gates whether an attempt starts) layered around them — so a rate-limiter acquire
 * timeout never reaches (and never counts against) retry/circuit-breaker state. Every provider
 * needs the same layering and differs only in which resilience4j instances back it and whether a
 * rate limiter applies at all, so this is built per provider via [Builder] rather than subclassed.
 */
class CallProtection private constructor(
    private val circuitBreaker: CircuitBreaker,
    private val retry: Retry,
    private val rateLimiter: RateLimiter?,
    private val rateLimitTimeoutEventName: String,
) {
    fun <T> execute(supplier: () -> T): T {
        val protected = { retry.executeSupplier { circuitBreaker.executeSupplier(supplier) } }
        val limiter = rateLimiter ?: return protected()
        return try {
            limiter.executeSupplier(protected)
        } catch (e: RequestNotPermitted) {
            // Reflects our own offered load exceeding the rate limit, not a provider-side failure —
            // tracked distinctly so it's never conflated with circuit breaker failures.
            NewRelic.getAgent().insights.recordCustomEvent(rateLimitTimeoutEventName, emptyMap<String, Any>())
            throw e
        }
    }

    class Builder(
        private val name: String
    ) {
        private var circuitBreaker: CircuitBreaker? = null
        private var retry: Retry? = null
        private var rateLimiter: RateLimiter? = null
        private var rateLimitTimeoutEventName: String = "${name}RateLimitTimeout"

        fun circuitBreaker(registry: CircuitBreakerRegistry) = apply { circuitBreaker = registry.circuitBreaker(name) }

        fun retry(registry: RetryRegistry) = apply { retry = registry.retry(name) }

        fun rateLimiter(registry: RateLimiterRegistry, timeoutEventName: String = rateLimitTimeoutEventName) =
            apply {
                rateLimiter = registry.rateLimiter(name)
                rateLimitTimeoutEventName = timeoutEventName
            }

        fun build(): CallProtection =
            CallProtection(
                circuitBreaker ?: error("circuitBreaker(...) is required to build a CallProtection"),
                retry ?: error("retry(...) is required to build a CallProtection"),
                rateLimiter,
                rateLimitTimeoutEventName,
            )
    }
}
