package com.davismariotti.campalert.support

import com.davismariotti.campalert.api.model.LoginBody
import com.davismariotti.campalert.api.model.RegisterBody
import com.davismariotti.campalert.api.model.VerifyEmailBody
import com.davismariotti.campalert.recreation.RecreationApi
import com.davismariotti.campalert.recreation.RidbApi
import com.davismariotti.campalert.service.email.EmailSender
import com.davismariotti.campalert.service.email.MailSender
import com.davismariotti.campalert.service.sms.TwilioVerifyService
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("integrationtest")
open class IntegrationTestBase {
    companion object {
        // Singleton containers: started once per JVM, never restarted between test classes.
        // This keeps the ports stable so the cached Spring context remains valid across classes.
        val postgres: PostgreSQLContainer by lazy {
            PostgreSQLContainer("postgres:16-alpine")
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

    // Declared here so all subclasses share a single Spring context (same @MockitoBean set = same cache key).
    @MockitoBean
    protected lateinit var twilioVerifyService: TwilioVerifyService

    @MockitoBean
    protected lateinit var ridbApi: RidbApi

    @MockitoBean
    protected lateinit var recreationApi: RecreationApi

    // Satisfies TemplatedMailSender's constructor so the context loads; never called directly.
    @MockitoBean
    protected lateinit var emailSender: EmailSender

    // Replaces TemplatedMailSender so no real email is sent during tests.
    // Calls are captured in sentEmailVarsList so helpers can extract codes and tokens.
    @MockitoBean
    protected lateinit var mailSender: MailSender

    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var redisConnectionFactory: RedisConnectionFactory

    @Autowired
    protected lateinit var circuitBreakerRegistry: CircuitBreakerRegistry

    @Autowired
    protected lateinit var mapper: ObjectMapper

    /** All variables maps passed to mailSender.send() during this test, in the order they were sent. */
    protected val sentEmailVarsList = CopyOnWriteArrayList<Map<String, Any>>()

    @BeforeEach
    fun resetState() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE notification_outbox, search_request_checks, search_requests, " +
                "persistent_logins, phone_numbers, email_verifications, password_resets, users, shedlock CASCADE"
        )
        redisConnectionFactory.connection.use { it.serverCommands().flushAll() }
        listOf("ridb", "recreation-gov", "twilio").forEach { name ->
            circuitBreakerRegistry.circuitBreaker(name).reset()
        }

        sentEmailVarsList.clear()
        @Suppress("UNCHECKED_CAST")
        doAnswer { invocation ->
            sentEmailVarsList.add(invocation.arguments[3] as Map<String, Any>)
            null
        }.`when`(mailSender).send(anyString(), anyString(), anyString(), anyKt())
    }

    protected fun getCsrfToken(): String {
        val result = mockMvc.perform(get("/api/auth/me")).andReturn()
        return result.response.getCookie("XSRF-TOKEN")?.value
            ?: error("XSRF-TOKEN cookie not found in GET /api/auth/me response")
    }

    protected fun doPost(path: String, session: Cookie? = null, body: Any? = null): MvcResult {
        val csrf = getCsrfToken()
        var req = post(path)
            .cookie(Cookie("XSRF-TOKEN", csrf))
            .header("X-XSRF-TOKEN", csrf)
            .contentType(MediaType.APPLICATION_JSON)
        if (session != null) req = req.cookie(session)
        if (body != null) req = req.content(mapper.writeValueAsString(body))
        return mockMvc.perform(req).andReturn()
    }

    protected fun doPut(path: String, session: Cookie? = null, body: Any): MvcResult {
        val csrf = getCsrfToken()
        var req = put(path)
            .cookie(Cookie("XSRF-TOKEN", csrf))
            .header("X-XSRF-TOKEN", csrf)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(body))
        if (session != null) req = req.cookie(session)
        return mockMvc.perform(req).andReturn()
    }

    protected fun doPatch(path: String, session: Cookie? = null, body: Any): MvcResult {
        val csrf = getCsrfToken()
        var req = patch(path)
            .cookie(Cookie("XSRF-TOKEN", csrf))
            .header("X-XSRF-TOKEN", csrf)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(body))
        if (session != null) req = req.cookie(session)
        return mockMvc.perform(req).andReturn()
    }

    protected fun doDelete(path: String, session: Cookie? = null): MvcResult {
        val csrf = getCsrfToken()
        var req = delete(path)
            .cookie(Cookie("XSRF-TOKEN", csrf))
            .header("X-XSRF-TOKEN", csrf)
        if (session != null) req = req.cookie(session)
        return mockMvc.perform(req).andReturn()
    }

    @Suppress("UNCHECKED_CAST")
    protected fun <T> anyKt(): T = org.mockito.ArgumentMatchers.any<T>() as T

    protected fun extractId(result: MvcResult): Long = mapper.readTree(result.response.contentAsString).get("id").asLong()

    protected fun latestEmailVar(key: String, startIndex: Int = 0): Any {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            sentEmailVarsList
                .drop(startIndex)
                .asReversed()
                .firstOrNull { it.containsKey(key) }
                ?.get(key)
                ?.let { return it }
            Thread.sleep(25)
        }
        val capturedKeys = sentEmailVarsList.map { it.keys }.joinToString()
        error("No email captured with key '$key'. Captured keys: $capturedKeys")
    }

    protected fun verificationCodeFor(verificationId: String): String {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val emailVars =
                sentEmailVarsList
                    .asReversed()
                    .firstOrNull { (it["verifyUrl"] as? String)?.contains("verificationId=$verificationId") == true }
            (emailVars?.get("code") as? String)?.let { return it }
            Thread.sleep(25)
        }
        val verifyUrls = sentEmailVarsList.mapNotNull { it["verifyUrl"] as? String }
        error("No verification email captured for verificationId '$verificationId'. Captured verifyUrls: $verifyUrls")
    }

    /** Registers a new account and returns the verificationId from the 201 response body. */
    protected fun registerOnly(email: String = "user@test.com", password: String = "password1"): String {
        val result = doPost(
            "/api/auth/register",
            body = RegisterBody(email = email, password = password, timezone = "America/Los_Angeles"),
        )
        return mapper.readTree(result.response.contentAsString).get("verificationId").asText()
    }

    /**
     * Verifies the account using the code from the most recently captured email.
     * Call after registerOnly() to complete the verification step.
     */
    protected fun verifyLatestEmail(verificationId: String): MvcResult {
        val code = verificationCodeFor(verificationId)
        return doPost(
            "/api/auth/verify-email",
            body = VerifyEmailBody(verificationId = UUID.fromString(verificationId), code = code),
        )
    }

    protected fun registerAndLogin(email: String = "user@test.com", password: String = "password1"): Cookie {
        val verificationId = registerOnly(email, password)
        verifyLatestEmail(verificationId)
        val result = doPost("/api/auth/login", body = LoginBody(email = email, password = password))
        return result.response.getCookie("SESSION") ?: error("SESSION cookie not found after login for $email")
    }
}
