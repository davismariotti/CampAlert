package com.davismariotti.campalert.service.email

import com.davismariotti.campalert.exception.ApiException
import org.springframework.http.HttpStatus

/** Failure outcomes of consuming a password reset token, mirroring [PasswordResetService.ResetResult]. */
sealed class PasswordResetException(
    code: String,
    message: String
) : ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message) {
    class InvalidOrExpired : PasswordResetException("RESET_INVALID_OR_EXPIRED", "Reset link is invalid or expired")

    class TooWeak : PasswordResetException("RESET_PASSWORD_TOO_WEAK", "Password does not meet requirements")

    class SameAsCurrent : PasswordResetException("RESET_PASSWORD_SAME_AS_CURRENT", "New password must differ from current password")
}
