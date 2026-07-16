package com.davismariotti.campalert.exception

import org.springframework.http.HttpStatus

/**
 * Base for exceptions that should be surfaced to API clients as an [com.davismariotti.campalert.api.model.ErrorResponse].
 * Caught globally by [GlobalExceptionHandler] — extenders must call super with the HTTP status and
 * the code/message to use in the response body.
 */
abstract class ApiException(
    val httpStatus: HttpStatus,
    val code: String?,
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
