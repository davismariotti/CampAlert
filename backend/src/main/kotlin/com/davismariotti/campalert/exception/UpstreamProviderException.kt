package com.davismariotti.campalert.exception

import org.springframework.http.HttpStatus

class UpstreamProviderException(
    message: String = "Recreation.gov upstream error"
) : ApiException(HttpStatus.BAD_GATEWAY, null, message)
