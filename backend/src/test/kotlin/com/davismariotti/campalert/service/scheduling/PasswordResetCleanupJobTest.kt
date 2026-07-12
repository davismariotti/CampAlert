package com.davismariotti.campalert.service.scheduling

import com.davismariotti.campalert.config.PasswordResetProperties
import com.davismariotti.campalert.repository.PasswordResetRepository
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.argumentCaptor
import java.time.Duration
import java.time.Instant

class PasswordResetCleanupJobTest {
    private val passwordResetRepository = mock(PasswordResetRepository::class.java)
    private val props = PasswordResetProperties(cleanupRetention = Duration.ofDays(7))
    private val job = PasswordResetCleanupJob(passwordResetRepository, props)

    @Test
    fun `purgeExpired deletes rows older than the configured retention`() {
        `when`(passwordResetRepository.deleteExpiredBefore(anyKt())).thenReturn(2)

        val before = Instant.now()
        job.purgeExpired()
        val after = Instant.now()

        val captor = argumentCaptor<Instant>()
        verify(passwordResetRepository).deleteExpiredBefore(captor.capture())
        val cutoff = captor.firstValue

        assertTrue(!cutoff.isBefore(before.minus(props.cleanupRetention)), "cutoff should be no earlier than before - retention")
        assertTrue(!cutoff.isAfter(after.minus(props.cleanupRetention)), "cutoff should be no later than after - retention")
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyKt(): T = org.mockito.ArgumentMatchers.any<T>() as T
}
