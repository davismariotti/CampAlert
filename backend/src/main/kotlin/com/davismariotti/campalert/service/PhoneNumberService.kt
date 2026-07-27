package com.davismariotti.campalert.service

import com.davismariotti.campalert.model.PauseReason
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
            val active = searchRequestRepository.findActiveUnpausedByUserId(userId)
            active.forEach {
                it.state.pauseReason = PauseReason.NO_VERIFIED_PHONE.name
                searchRequestRepository.save(it)
            }
        }
    }

    fun resumeRequestsIfVerifiedPhone(userId: Long) {
        val paused = searchRequestRepository.findByUserIdAndPauseReason(userId, PauseReason.NO_VERIFIED_PHONE.name)
        paused.forEach {
            it.state.pauseReason = null
            searchRequestRepository.save(it)
        }
    }

    fun supersedePreviousVerifiedPhone(userId: Long, keepId: Long) {
        val previous = phoneNumberRepository
            .findByUserIdAndStatus(userId, PhoneNumberStatus.VERIFIED)
            .filter { it.id != keepId }
        phoneNumberRepository.deleteAll(previous)
    }
}
