package com.davismariotti.campalert.exception

import com.davismariotti.campalert.api.model.ErrorResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(ApiException::class)
    fun handleApiException(e: ApiException): ResponseEntity<ErrorResponse> = ResponseEntity.status(e.httpStatus).body(ErrorResponse(message = e.message, code = e.code))
}
