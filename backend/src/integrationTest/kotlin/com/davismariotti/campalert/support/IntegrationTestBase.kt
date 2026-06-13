package com.davismariotti.campalert.support

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("integrationtest")
open class IntegrationTestBase {
    companion object {
        // Singleton containers: started once per JVM, never restarted between test classes.
        // This keeps the ports stable so the cached Spring context remains valid across classes.
        val postgres: PostgreSQLContainer<Nothing> by lazy {
            PostgreSQLContainer<Nothing>("postgres:16-alpine")
                .also { it.withInitScript("schema.sql") }
                .also { it.start() }
        }

        val redis: GenericContainer<Nothing> by lazy {
            GenericContainer<Nothing>("redis:7-alpine")
                .also { it.withExposedPorts(6379) }
                .also { it.start() }
        }

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.data.redis.host", redis::getHost)
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
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
