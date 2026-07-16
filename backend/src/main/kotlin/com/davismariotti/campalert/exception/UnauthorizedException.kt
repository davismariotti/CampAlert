package com.davismariotti.campalert.exception

import org.springframework.http.HttpStatus

class UnauthorizedException(
    message: String,
    code: String? = null
) : ApiException(HttpStatus.UNAUTHORIZED, code, message)
