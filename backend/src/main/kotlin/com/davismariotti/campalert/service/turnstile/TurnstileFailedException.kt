package com.davismariotti.campalert.service.turnstile

import com.davismariotti.campalert.exception.ApiException
import org.springframework.http.HttpStatus

class TurnstileFailedException :
    ApiException(
        httpStatus = HttpStatus.FORBIDDEN,
        code = "TURNSTILE_FAILED",
        message = "Bot verification failed",
    )
