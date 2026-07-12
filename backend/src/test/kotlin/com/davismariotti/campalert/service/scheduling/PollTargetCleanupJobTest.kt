package com.davismariotti.campalert.service.scheduling

import com.davismariotti.campalert.repository.PollTargetStateDao
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

class PollTargetCleanupJobTest {
    private val dao = mock(PollTargetStateDao::class.java)
    private val job = PollTargetCleanupJob(dao, cleanupThresholdMs = 604_800_000L)

    @Test
    fun `delegates to the dao with the configured threshold`() {
        `when`(dao.deleteStaleOrphans(any(), eq(604_800_000L))).thenReturn(3)

        job.cleanup()

        verify(dao).deleteStaleOrphans(any(), eq(604_800_000L))
    }

    @Test
    fun `zero deletions does not throw`() {
        `when`(dao.deleteStaleOrphans(any(), any())).thenReturn(0)

        job.cleanup()
    }
}
