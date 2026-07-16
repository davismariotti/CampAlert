package com.davismariotti.campalert.service.turnstile

import com.davismariotti.campalert.config.TurnstileProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Verifies Cloudflare Turnstile tokens via the siteverify endpoint. Fails open (returns true) on a
 * network error, timeout, or malformed/non-2xx response — distinct from a definitive `success: false`
 * response, which returns false. See design.md D3 in the add-turnstile-bot-protection change for the
 * fail-open rationale.
 */
@Service
class TurnstileService(
    private val turnstileApi: TurnstileApi,
    private val props: TurnstileProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun verify(token: String): Boolean =
        try {
            val response = turnstileApi.siteverify(props.secretKey, token).execute().body()
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
}
