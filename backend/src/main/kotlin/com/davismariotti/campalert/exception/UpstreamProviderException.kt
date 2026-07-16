package com.davismariotti.campalert.exception

import org.springframework.http.HttpStatus

class UpstreamProviderException(
    message: String = "Recreation.gov upstream error",
    cause: Throwable? = null,
) : ApiException(HttpStatus.BAD_GATEWAY, null, message, cause)
