package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.PhoneNumbersApiDelegate
import com.davismariotti.campalert.api.model.AddPhoneNumberBody
import com.davismariotti.campalert.api.model.ErrorResponse
import com.davismariotti.campalert.api.model.PhoneNumberResponse
import com.davismariotti.campalert.api.model.VerifyPhoneNumberBody
import com.davismariotti.campalert.model.PhoneNumber
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.PhoneNumberService
import com.davismariotti.campalert.service.sms.TwilioVerifyService
import com.davismariotti.campalert.service.sms.VerifyResult
import com.twilio.exception.TwilioException
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class PhoneNumbersDelegateImpl(
    private val phoneNumberRepository: PhoneNumberRepository,
    private val userRepository: UserRepository,
    private val twilioVerifyService: TwilioVerifyService,
    private val phoneNumberService: PhoneNumberService,
) : PhoneNumbersApiDelegate {
    private fun currentUserId(): Long {
        val email = SecurityContextHolder.getContext().authentication.name
        return userRepository.findByEmail(email)!!.id!!
    }

    @PreAuthorize("isAuthenticated()")
    override fun listPhoneNumbers(): ResponseEntity<List<PhoneNumberResponse>> {
        val userId = currentUserId()
        return ResponseEntity.ok(phoneNumberRepository.findByUserId(userId).map { it.toResponse() })
    }

    @Suppress("UNCHECKED_CAST")
    @PreAuthorize("isAuthenticated()")
    override fun addPhoneNumber(addPhoneNumberBody: AddPhoneNumberBody): ResponseEntity<PhoneNumberResponse> {
        if (!addPhoneNumberBody.smsConsent) {
            return error(400, "SMS consent is required") as ResponseEntity<PhoneNumberResponse>
        }

        val e164Regex = Regex("^\\+[1-9]\\d{1,14}$")
        if (!e164Regex.matches(addPhoneNumberBody.phone)) {
            return error(400, "Phone number must be in E.164 format") as ResponseEntity<PhoneNumberResponse>
        }

        if (phoneNumberRepository.findByPhone(addPhoneNumberBody.phone) != null) {
            return error(
                409,
                "Phone number is already registered",
                "PHONE_ALREADY_REGISTERED"
            ) as ResponseEntity<PhoneNumberResponse>
        }

        return try {
            twilioVerifyService.startVerification(addPhoneNumberBody.phone)
            val saved =
                phoneNumberRepository.save(
                    PhoneNumber(
                        userId = currentUserId(),
                        phone = addPhoneNumberBody.phone,
                        status = PhoneNumberStatus.PENDING_VERIFICATION,
                        smsConsentAt = Instant.now(),
                    ),
                )
            ResponseEntity.status(201).body(saved.toResponse())
        } catch (e: TwilioException) {
            error(502, "Failed to send verification code. Please try again.") as ResponseEntity<PhoneNumberResponse>
        }
    }

    @Suppress("UNCHECKED_CAST")
    @PreAuthorize("isAuthenticated()")
    override fun verifyPhoneNumber(
        id: Long,
        verifyPhoneNumberBody: VerifyPhoneNumberBody,
    ): ResponseEntity<PhoneNumberResponse> {
        val userId = currentUserId()
        val phoneNumber =
            phoneNumberRepository.findById(id).orElse(null)
                ?.takeIf { it.userId == userId }
                ?: return ResponseEntity.notFound().build()

        if (phoneNumber.status != PhoneNumberStatus.PENDING_VERIFICATION) {
            return error(
                422,
                "Phone number is not pending verification",
                "NOT_PENDING_VERIFICATION"
            ) as ResponseEntity<PhoneNumberResponse>
        }

        return when (twilioVerifyService.checkVerification(phoneNumber.phone, verifyPhoneNumberBody.code)) {
            VerifyResult.Approved -> {
                val verified =
                    phoneNumberRepository.save(
                        phoneNumber.copy(
                            status = PhoneNumberStatus.VERIFIED,
                            verifiedAt = Instant.now(),
                        ),
                    )
                phoneNumberService.resumeRequestsIfVerifiedPhone(userId)
                ResponseEntity.ok(verified.toResponse())
            }
            VerifyResult.InvalidCode ->
                error(422, "Invalid verification code", "INVALID_OTP") as ResponseEntity<PhoneNumberResponse>
            VerifyResult.Expired ->
                error(422, "Verification code has expired", "OTP_EXPIRED") as ResponseEntity<PhoneNumberResponse>
        }
    }

    @PreAuthorize("isAuthenticated()")
    override fun deletePhoneNumber(id: Long): ResponseEntity<Unit> {
        val userId = currentUserId()
        val phoneNumber =
            phoneNumberRepository.findById(id).orElse(null)
                ?.takeIf { it.userId == userId }
                ?: return ResponseEntity.notFound().build()
        phoneNumberRepository.delete(phoneNumber)
        phoneNumberService.pauseRequestsIfNoVerifiedPhone(userId)
        return ResponseEntity.noContent().build()
    }

    private fun error(status: Int, message: String, code: String? = null) =
        ResponseEntity.status(status).body(ErrorResponse(message = message, code = code))

    private fun PhoneNumber.toResponse() =
        PhoneNumberResponse(
            id = this.id!!,
            phone = this.phone,
            status = PhoneNumberResponse.Status.valueOf(this.status.name),
            smsConsentAt = OffsetDateTime.ofInstant(this.smsConsentAt, ZoneOffset.UTC),
            createdAt = OffsetDateTime.ofInstant(this.createdAt, ZoneOffset.UTC),
            firstMessageSent = this.firstMessageSent,
            verifiedAt = this.verifiedAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) },
            requiresCarrierOptIn = false,
        )
}
