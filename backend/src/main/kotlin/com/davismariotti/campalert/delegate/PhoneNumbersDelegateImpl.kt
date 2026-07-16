package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.PhoneNumbersApiDelegate
import com.davismariotti.campalert.api.model.AddPhoneNumberBody
import com.davismariotti.campalert.api.model.PhoneNumberResponse
import com.davismariotti.campalert.api.model.VerifyPhoneNumberBody
import com.davismariotti.campalert.exception.NotFoundException
import com.davismariotti.campalert.model.PhoneNumber
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.PhoneNumberException
import com.davismariotti.campalert.service.PhoneNumberService
import com.davismariotti.campalert.service.sms.TwilioVerifyService
import com.davismariotti.campalert.service.sms.VerifyResult
import com.davismariotti.campalert.service.turnstile.TurnstileService
import com.davismariotti.campalert.util.currentUserId
import com.twilio.exception.TwilioException
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
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
    private val turnstileService: TurnstileService,
) : PhoneNumbersApiDelegate {
    private fun currentUserId(): Long = currentUserId(userRepository)

    @PreAuthorize("isAuthenticated()")
    override fun listPhoneNumbers(): ResponseEntity<List<PhoneNumberResponse>> {
        val userId = currentUserId()
        return ResponseEntity.ok(phoneNumberRepository.findByUserId(userId).map { it.toResponse() })
    }

    @PreAuthorize("isAuthenticated()")
    override fun addPhoneNumber(addPhoneNumberBody: AddPhoneNumberBody): ResponseEntity<PhoneNumberResponse> {
        turnstileService.verify(addPhoneNumberBody.turnstileToken)
        if (!addPhoneNumberBody.smsConsent) {
            throw PhoneNumberException.SmsConsentRequired()
        }

        val e164Regex = Regex("^\\+[1-9]\\d{1,14}$")
        if (!e164Regex.matches(addPhoneNumberBody.phone)) {
            throw PhoneNumberException.InvalidPhoneFormat()
        }

        if (phoneNumberRepository.findByPhone(addPhoneNumberBody.phone) != null) {
            throw PhoneNumberException.AlreadyRegistered()
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
            throw PhoneNumberException.VerificationSendFailed(e)
        }
    }

    @PreAuthorize("isAuthenticated()")
    override fun verifyPhoneNumber(
        id: Long,
        verifyPhoneNumberBody: VerifyPhoneNumberBody,
    ): ResponseEntity<PhoneNumberResponse> {
        val userId = currentUserId()
        val phoneNumber =
            phoneNumberRepository
                .findById(id)
                .orElse(null)
                ?.takeIf { it.userId == userId }
                ?: throw NotFoundException("Phone number not found")

        if (phoneNumber.status != PhoneNumberStatus.PENDING_VERIFICATION) {
            throw PhoneNumberException.NotPendingVerification()
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
                phoneNumberService.supersedePreviousVerifiedPhone(userId, verified.id!!)
                phoneNumberService.resumeRequestsIfVerifiedPhone(userId)
                ResponseEntity.ok(verified.toResponse())
            }
            VerifyResult.InvalidCode -> throw PhoneNumberException.VerificationCodeInvalid()
            VerifyResult.Expired -> throw PhoneNumberException.VerificationCodeExpired()
        }
    }

    @PreAuthorize("isAuthenticated()")
    override fun deletePhoneNumber(id: Long): ResponseEntity<Unit> {
        val userId = currentUserId()
        val phoneNumber =
            phoneNumberRepository
                .findById(id)
                .orElse(null)
                ?.takeIf { it.userId == userId }
                ?: throw NotFoundException("Phone number not found")
        phoneNumberRepository.delete(phoneNumber)
        phoneNumberService.pauseRequestsIfNoVerifiedPhone(userId)
        return ResponseEntity.noContent().build()
    }

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
