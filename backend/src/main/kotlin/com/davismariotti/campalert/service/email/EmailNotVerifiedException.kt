package com.davismariotti.campalert.service.email

import com.davismariotti.campalert.exception.ApiException
import org.springframework.http.HttpStatus
import java.util.UUID

/**
 * Thrown on login when the account's email hasn't been verified yet. Carries [verificationId] so the
 * client can resume the pending verification flow — handled specially by
 * [com.davismariotti.campalert.exception.GlobalExceptionHandler] to surface that field on the response.
 */
class EmailNotVerifiedException(
    val verificationId: UUID
) : ApiException(HttpStatus.UNAUTHORIZED, "EMAIL_NOT_VERIFIED", "Email not verified")
