package com.davismariotti.campalert.exception

import org.springframework.http.HttpStatus

class NotFoundException(
    message: String
) : ApiException(HttpStatus.NOT_FOUND, null, message)
