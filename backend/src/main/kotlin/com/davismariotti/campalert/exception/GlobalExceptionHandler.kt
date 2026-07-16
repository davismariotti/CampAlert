package com.davismariotti.campalert.exception

import com.davismariotti.campalert.api.model.ErrorResponse
import com.davismariotti.campalert.service.email.EmailNotVerifiedException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    // More specific than handleApiException below — Spring's handler resolution picks the closest
    // match in the exception hierarchy, so EmailNotVerifiedException instances land here instead.
    @ExceptionHandler(EmailNotVerifiedException::class)
    fun handleEmailNotVerified(e: EmailNotVerifiedException): ResponseEntity<ErrorResponse> = ResponseEntity.status(e.httpStatus).body(ErrorResponse(message = e.message, code = e.code, verificationId = e.verificationId))

    @ExceptionHandler(ApiException::class)
    fun handleApiException(e: ApiException): ResponseEntity<ErrorResponse> = ResponseEntity.status(e.httpStatus).body(ErrorResponse(message = e.message, code = e.code))
}
