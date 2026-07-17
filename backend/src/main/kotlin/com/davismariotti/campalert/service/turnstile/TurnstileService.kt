package com.davismariotti.campalert.service.turnstile

import com.davismariotti.campalert.config.TurnstileProperties
import com.davismariotti.campalert.provider.CallProtection
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

/**
 * Verifies Cloudflare Turnstile tokens via the siteverify endpoint. Fails open (returns true) on a
 * network error, timeout, or malformed/non-2xx response — distinct from a definitive `success: false`
 * response, which returns false. See design.md D3 in the add-turnstile-bot-protection change for the
 * fail-open rationale. That fail-open behavior extends to the circuit breaker: once
 * [callProtection] trips (or a retry exhausts), [CallProtection.execute] throws the same way a
 * direct call failure would, and is caught below exactly the same way.
 */
@Service
class TurnstileService(
    private val turnstileApi: TurnstileApi,
    private val props: TurnstileProperties,
    @Qualifier("turnstileCallProtection") private val callProtection: CallProtection,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** @throws TurnstileFailedException if [token] fails verification. */
    fun verify(token: String) {
        val success = try {
            val response = callProtection.execute { turnstileApi.siteverify(props.secretKey, token).execute() }.body()
            if (response == null) {
                log.warn("Turnstile siteverify returned an empty response body; failing open")
                true
            } else {
                response.success
            }
        } catch (e: Exception) {
            log.warn("Turnstile siteverify call failed; failing open", e)
            true
        }
        if (!success) throw TurnstileFailedException()
    }
}
