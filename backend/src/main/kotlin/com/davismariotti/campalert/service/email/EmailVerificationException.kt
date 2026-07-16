package com.davismariotti.campalert.service.email

import com.davismariotti.campalert.exception.ApiException
import org.springframework.http.HttpStatus

/** Failure outcomes of consuming an email verification code, mirroring [EmailVerificationService.VerifyResult]. */
sealed class EmailVerificationException(
    code: String,
    message: String
) : ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message) {
    class AttemptsExceeded : EmailVerificationException("VERIFICATION_CODE_ATTEMPTS_EXCEEDED", "Attempt limit reached")

    class CodeInvalid : EmailVerificationException("VERIFICATION_CODE_INVALID", "Invalid verification code")

    class LinkExpired : EmailVerificationException("VERIFICATION_INVALID_OR_EXPIRED", "Verification link is invalid or expired")
}
