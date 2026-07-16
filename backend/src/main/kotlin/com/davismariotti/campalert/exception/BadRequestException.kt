package com.davismariotti.campalert.exception

import org.springframework.http.HttpStatus

class BadRequestException(
    message: String,
    code: String? = null
) : ApiException(HttpStatus.BAD_REQUEST, code, message)
