package com.davismariotti.campalert.support

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("integrationtest")
open class IntegrationTestBase {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<Nothing> = PostgreSQLContainer<Nothing>("postgres:16-alpine")
            .also { it.withInitScript("schema.sql") }

        @Container
        @ServiceConnection
        @JvmStatic
        val redis: GenericContainer<Nothing> = GenericContainer<Nothing>("redis:7-alpine")
            .also { it.withExposedPorts(6379) }
    }

    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var redisConnectionFactory: RedisConnectionFactory

    @BeforeEach
    fun resetState() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE notification_outbox, search_request_checks, search_requests_v2, " +
                "persistent_logins, phone_numbers, users, search_requests, shedlock CASCADE"
        )
        redisConnectionFactory.connection.use { it.serverCommands().flushAll() }
    }

    protected fun getCsrfToken(): String {
        val result = mockMvc.perform(get("/api/auth/me")).andReturn()
        return result.response.getCookie("XSRF-TOKEN")?.value
            ?: error("XSRF-TOKEN cookie not found in GET /api/auth/me response")
    }
}
