package com.davismariotti.campalert.exception

import org.springframework.http.HttpStatus

/** Thrown when creating a search request would violate the requesting user's effective provider access or quota (see UserResourceLimitsService). */
sealed class ResourceLimitException(
    code: String,
    message: String,
) : ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message) {
    class ProviderNotAllowed(
        providerName: String
    ) : ResourceLimitException("PROVIDER_NOT_ALLOWED", "You do not have access to $providerName.")

    class CombinedQuotaExceeded(
        limit: Int
    ) : ResourceLimitException("QUOTA_EXCEEDED", "You have reached your limit of $limit active search requests.")

    class ProviderQuotaExceeded(
        providerName: String,
        limit: Int
    ) : ResourceLimitException("PROVIDER_QUOTA_EXCEEDED", "You have reached your limit of $limit active $providerName search requests.")
}
