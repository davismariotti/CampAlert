package com.davismariotti.campalert.exception

import org.springframework.http.HttpStatus

class ConflictException(
    message: String,
    code: String? = null
) : ApiException(HttpStatus.CONFLICT, code, message)
