package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.recreation.PermitZoneAvailabilityCell
import com.davismariotti.campalert.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * [ZoneAvailabilityBaselineServiceTest] covers the transition logic against a mocked
 * StringRedisTemplate. This class instead runs the service against the real Redis testcontainer
 * [IntegrationTestBase] provisions, so the actual JSON serialize/write/read round-trip and TTL are
 * exercised for real rather than assumed from a stub.
 */
class ZoneAvailabilityBaselineServiceIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var service: ZoneAvailabilityBaselineService

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    private val month = YearMonth.of(2026, 7)
    private val date = ZonedDateTime.of(2026, 7, 13, 0, 0, 0, 0, ZoneOffset.UTC)

    @Test
    fun `first tick for a division is never suspicious and persists a baseline in Redis`() {
        val cell = PermitZoneAvailabilityCell(total = 25, remaining = 3)

        val result = service.looksSuspicious("233261", "343", month, mapOf(date to cell))

        assertThat(result).isFalse()
        assertThat(redisTemplate.opsForValue().get("permit:zone-baseline:233261:343:2026-07")).isNotNull()
    }

    @Test
    fun `a date that flips from partially-booked to fully-open across real ticks is flagged suspicious`() {
        service.looksSuspicious("233261", "343", month, mapOf(date to PermitZoneAvailabilityCell(total = 25, remaining = 3)))

        val result = service.looksSuspicious("233261", "343", month, mapOf(date to PermitZoneAvailabilityCell(total = 25, remaining = 25)))

        assertThat(result).isTrue()
    }

    @Test
    fun `a date that stays fully-open across real ticks is not flagged`() {
        service.looksSuspicious("233261", "343", month, mapOf(date to PermitZoneAvailabilityCell(total = 25, remaining = 25)))

        val result = service.looksSuspicious("233261", "343", month, mapOf(date to PermitZoneAvailabilityCell(total = 25, remaining = 25)))

        assertThat(result).isFalse()
    }

    @Test
    fun `baselines for different divisions on the same permit and month are stored independently`() {
        service.looksSuspicious("233261", "343", month, mapOf(date to PermitZoneAvailabilityCell(total = 25, remaining = 3)))

        // Division "290" has never been recorded, so its first tick must be judged on its own — not
        // against "343"'s baseline — even though both share the same permit and month.
        val result = service.looksSuspicious("233261", "290", month, mapOf(date to PermitZoneAvailabilityCell(total = 25, remaining = 25)))

        assertThat(result).isFalse()
    }

    @Test
    fun `baseline is written with the configured TTL, not persisted forever`() {
        service.looksSuspicious("233261", "343", month, mapOf(date to PermitZoneAvailabilityCell(total = 25, remaining = 3)))

        val ttlSeconds = redisTemplate.getExpire("permit:zone-baseline:233261:343:2026-07", TimeUnit.SECONDS)

        // campfinder.permit.zone-baseline-ttl-minutes defaults to 30; assert a sane bound rather than
        // an exact value to avoid coupling this test to the literal default.
        assertThat(ttlSeconds).isGreaterThan(0).isLessThanOrEqualTo(TimeUnit.MINUTES.toSeconds(30))
    }
}
