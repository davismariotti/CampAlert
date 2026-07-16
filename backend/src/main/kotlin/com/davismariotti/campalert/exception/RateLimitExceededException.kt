package com.davismariotti.campalert.exception

import org.springframework.http.HttpStatus

class RateLimitExceededException(
    message: String = "Too many requests, please try again later"
) : ApiException(HttpStatus.TOO_MANY_REQUESTS, null, message)
