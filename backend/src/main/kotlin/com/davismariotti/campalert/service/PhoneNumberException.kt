package com.davismariotti.campalert.service

import com.davismariotti.campalert.exception.ApiException
import org.springframework.http.HttpStatus

/** Every error outcome for phone-number registration and OTP verification. */
sealed class PhoneNumberException(
    httpStatus: HttpStatus,
    code: String?,
    message: String
) : ApiException(httpStatus, code, message) {
    class SmsConsentRequired : PhoneNumberException(HttpStatus.BAD_REQUEST, null, "SMS consent is required")

    class InvalidPhoneFormat : PhoneNumberException(HttpStatus.BAD_REQUEST, null, "Phone number must be in E.164 format")

    class AlreadyRegistered : PhoneNumberException(HttpStatus.CONFLICT, "PHONE_ALREADY_REGISTERED", "Phone number is already registered")

    class VerificationSendFailed : PhoneNumberException(HttpStatus.BAD_GATEWAY, null, "Failed to send verification code. Please try again.")

    class NotPendingVerification : PhoneNumberException(HttpStatus.UNPROCESSABLE_ENTITY, "NOT_PENDING_VERIFICATION", "Phone number is not pending verification")

    class VerificationCodeInvalid : PhoneNumberException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_OTP", "Invalid verification code")

    class VerificationCodeExpired : PhoneNumberException(HttpStatus.UNPROCESSABLE_ENTITY, "OTP_EXPIRED", "Verification code has expired")
}
