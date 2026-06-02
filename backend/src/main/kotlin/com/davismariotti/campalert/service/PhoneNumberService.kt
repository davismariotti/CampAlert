package com.davismariotti.campalert.service

import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import org.springframework.stereotype.Service

@Service
class PhoneNumberService(
    private val phoneNumberRepository: PhoneNumberRepository,
    private val searchRequestRepository: SearchRequestRepository,
) {
    fun pauseRequestsIfNoVerifiedPhone(userId: Long) {
        val hasVerified = phoneNumberRepository.countByUserIdAndStatus(userId, PhoneNumberStatus.VERIFIED) > 0
        if (!hasVerified) {
            val active = searchRequestRepository.findByUserIdAndCompletedFalseAndPauseReasonIsNull(userId)
            active.forEach { searchRequestRepository.save(it.copy(pauseReason = NO_PHONE)) }
        }
    }

    fun resumeRequestsIfVerifiedPhone(userId: Long) {
        val paused = searchRequestRepository.findByUserIdAndPauseReason(userId, NO_PHONE)
        paused.forEach { searchRequestRepository.save(it.copy(pauseReason = null)) }
    }

    companion object {
        const val NO_PHONE = "NO_VERIFIED_PHONE"
    }
}
