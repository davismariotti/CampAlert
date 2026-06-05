package com.davismariotti.campalert.service.sms

import com.twilio.exception.ApiException
import com.twilio.rest.verify.v2.service.Verification
import com.twilio.rest.verify.v2.service.VerificationCheck
import org.springframework.stereotype.Service

sealed class VerifyResult {
    data object Approved : VerifyResult()

    data object InvalidCode : VerifyResult()

    data object Expired : VerifyResult()
}

@Service
class TwilioVerifyService(
    private val twilioConfiguration: TwilioConfiguration,
) {
    fun startVerification(phone: String) {
        Verification.creator(twilioConfiguration.verifyServiceSid, phone, "sms").create()
    }

    fun checkVerification(phone: String, code: String): VerifyResult =
        try {
            val check =
                VerificationCheck
                    .creator(twilioConfiguration.verifyServiceSid)
                    .setTo(phone)
                    .setCode(code)
                    .create()
            if (check.status == "approved") VerifyResult.Approved else VerifyResult.InvalidCode
        } catch (e: ApiException) {
            if (e.statusCode == 404) VerifyResult.Expired else throw e
        }
}
