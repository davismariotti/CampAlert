package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.PollTargetId
import com.davismariotti.campalert.model.Provider
import com.davismariotti.campalert.model.TargetType
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.sql.ResultSet
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PollTargetStateDaoTest {
    private val jdbcTemplate = mock(JdbcTemplate::class.java)
    private val dao = PollTargetStateDao(jdbcTemplate)

    @Test
    fun `ensureTarget upserts on the full target_type, provider, target_id key and passes provider through`() {
        dao.ensureTarget(TargetType.CAMPGROUND, Provider.RECREATION_GOV, "233359", 120_000L)

        val sqlCaptor = ArgumentCaptor.forClass(String::class.java)
        val argsCaptor = ArgumentCaptor.forClass(Any::class.java)
        verify(jdbcTemplate).update(sqlCaptor.capture(), argsCaptor.capture(), argsCaptor.capture(), argsCaptor.capture(), argsCaptor.capture(), argsCaptor.capture())

        assertTrue(sqlCaptor.value.contains("ON CONFLICT (target_type, provider, target_id) DO NOTHING"))
        assertEquals(listOf("CAMPGROUND", "RECREATION_GOV", "233359"), argsCaptor.allValues.take(3))
    }

    @Test
    fun `two targets sharing target_type and target_id but different providers issue independent upserts`() {
        dao.ensureTarget(TargetType.CAMPGROUND, Provider.RECREATION_GOV, "233359", 120_000L)
        dao.ensureTarget(TargetType.CAMPGROUND, Provider.RECREATION_GOV, "233359", 300_000L)

        verify(jdbcTemplate, times(2)).update(any<String>(), any(), any(), any(), any(), any())
    }

    @Test
    fun `claimDue row mapper populates provider on the returned PollTargetId`() {
        val rs = mock(ResultSet::class.java)
        `when`(rs.getString("target_type")).thenReturn("PERMIT")
        `when`(rs.getString("provider")).thenReturn("RECREATION_GOV")
        `when`(rs.getString("target_id")).thenReturn("abc123")

        dao.claimDue(Instant.now(), Instant.now().plusSeconds(60))

        @Suppress("UNCHECKED_CAST")
        val rowMapperCaptor = ArgumentCaptor.forClass(RowMapper::class.java) as ArgumentCaptor<RowMapper<PollTargetId>>
        verify(jdbcTemplate).query(any<String>(), rowMapperCaptor.capture(), any(), any(), any())
        val mapped = rowMapperCaptor.value.mapRow(rs, 0)
        assertEquals(PollTargetId(TargetType.PERMIT, Provider.RECREATION_GOV, "abc123"), mapped)
    }
}
