package com.davismariotti.campalert.provider.camplife

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import org.springframework.stereotype.Component

/**
 * CampLife-specific call protection (circuit breaker + retry), kept entirely independent from
 * [com.davismariotti.campalert.provider.recreation.RecreationGovCallProtection] so a CampLife outage or
 * future rate-limiting can never open Recreation.gov's circuit (or vice versa).
 */
@Component
class CampLifeCallProtection(
    circuitBreakerRegistry: CircuitBreakerRegistry,
    retryRegistry: RetryRegistry,
) {
    private val retry = retryRegistry.retry("camplife")
    private val circuitBreaker = circuitBreakerRegistry.circuitBreaker("camplife")

    fun <T> execute(supplier: () -> T): T =
        retry.executeSupplier {
            circuitBreaker.executeSupplier(supplier)
        }
}
