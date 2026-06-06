package com.davismariotti.campalert.service

import com.davismariotti.campalert.model.PhoneNumber
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Instant

class PhoneNumberServiceTest {
    private val phoneNumberRepository = mock(PhoneNumberRepository::class.java)
    private val searchRequestRepository = mock(SearchRequestRepository::class.java)
    private val service = PhoneNumberService(phoneNumberRepository, searchRequestRepository)

    private fun phone(id: Long, status: PhoneNumberStatus) =
        PhoneNumber(
            id = id,
            userId = 1L,
            phone = "+1555000000$id",
            status = status,
            smsConsentAt = Instant.now(),
        )

    @Test
    fun `supersedePreviousVerifiedPhone deletes existing verified phone`() {
        val old = phone(10L, PhoneNumberStatus.VERIFIED)
        val newId = 20L
        `when`(phoneNumberRepository.findByUserIdAndStatus(1L, PhoneNumberStatus.VERIFIED))
            .thenReturn(listOf(old))

        service.supersedePreviousVerifiedPhone(userId = 1L, keepId = newId)

        verify(phoneNumberRepository).deleteAll(listOf(old))
    }

    @Test
    fun `supersedePreviousVerifiedPhone does not delete the kept phone`() {
        val kept = phone(20L, PhoneNumberStatus.VERIFIED)
        `when`(phoneNumberRepository.findByUserIdAndStatus(1L, PhoneNumberStatus.VERIFIED))
            .thenReturn(listOf(kept))

        service.supersedePreviousVerifiedPhone(userId = 1L, keepId = 20L)

        verify(phoneNumberRepository).deleteAll(emptyList())
    }

    @Test
    fun `supersedePreviousVerifiedPhone is no-op when no previous verified phone`() {
        `when`(phoneNumberRepository.findByUserIdAndStatus(1L, PhoneNumberStatus.VERIFIED))
            .thenReturn(emptyList())

        service.supersedePreviousVerifiedPhone(userId = 1L, keepId = 99L)

        verify(phoneNumberRepository).deleteAll(emptyList())
    }
}
