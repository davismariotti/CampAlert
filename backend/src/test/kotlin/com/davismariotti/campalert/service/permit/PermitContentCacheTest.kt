package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.recreation.PermitContentPayload
import com.davismariotti.campalert.recreation.PermitContentResponse
import com.davismariotti.campalert.recreation.PermitDivisionContent
import com.davismariotti.campalert.recreation.PermitDivisionType
import com.davismariotti.campalert.recreation.PermitRuleContent
import com.davismariotti.campalert.recreation.RecreationApi
import com.davismariotti.campalert.service.redis.RedisJsonCache
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import retrofit2.Call
import retrofit2.Response
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.util.concurrent.TimeUnit

class PermitContentCacheTest {
    private val recreationApi = mock(RecreationApi::class.java)
    private val redisTemplate = mock(StringRedisTemplate::class.java)

    @Suppress("UNCHECKED_CAST")
    private val valueOps = mock(ValueOperations::class.java) as ValueOperations<String, String>
    private val objectMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    private val redisJsonCache = RedisJsonCache(redisTemplate, objectMapper)

    private val circuitBreakerRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
    private val retryRegistry = RetryRegistry.of(RetryConfig.ofDefaults())

    private val cache = PermitContentCache(recreationApi, redisJsonCache, circuitBreakerRegistry, retryRegistry, 2L)

    @BeforeEach
    fun setUp() {
        `when`(redisTemplate.opsForValue()).thenReturn(valueOps)
        circuitBreakerRegistry.circuitBreaker("recreation-gov").reset()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> mockCall(body: T): Call<T> {
        val call = mock(Call::class.java) as Call<T>
        `when`(call.execute()).thenReturn(Response.success(body))
        return call
    }

    @Test
    fun `cache miss fetches and writes back with configured TTL`() {
        `when`(valueOps.get("permit:content:233261")).thenReturn(null)
        val payload = PermitContentPayload(divisions = mapOf("343" to PermitDivisionContent(id = "343")))
        val call = mockCall(PermitContentResponse(payload))
        `when`(recreationApi.getPermitContent("233261")).thenReturn(call)

        val result = cache.get("233261")

        assertEquals(payload, result)
        verify(valueOps).set(eq("permit:content:233261"), any(), eq(2L), eq(TimeUnit.HOURS))
    }

    @Test
    fun `cache hit does not refetch`() {
        val payload = PermitContentPayload(divisions = mapOf("343" to PermitDivisionContent(id = "343")))
        `when`(valueOps.get("permit:content:233261")).thenReturn(objectMapper.writeValueAsString(payload))

        val result = cache.get("233261")

        assertEquals(payload, result)
        verify(recreationApi, never()).getPermitContent(anyString())
    }

    @Test
    fun `fetch failure returns null`() {
        `when`(valueOps.get("permit:content:233261")).thenReturn(null)
        val call = mock(Call::class.java)
        `when`(call.execute()).thenThrow(RuntimeException("down"))
        @Suppress("UNCHECKED_CAST")
        `when`(recreationApi.getPermitContent("233261")).thenReturn(call as Call<PermitContentResponse>)

        assertNull(cache.get("233261"))
    }

    @Test
    fun `falls back to permit details when permitcontent rules are empty`() {
        `when`(valueOps.get("permit:content:233261")).thenReturn(null)
        val primaryPayload = PermitContentPayload(
            divisions = mapOf("343" to PermitDivisionContent(id = "343", type = PermitDivisionType.DESTINATION_ZONE)),
            rules = emptyList(),
        )
        val primaryCall = mockCall(PermitContentResponse(primaryPayload))
        `when`(recreationApi.getPermitContent("233261")).thenReturn(primaryCall)

        val fallbackRule = PermitRuleContent(divisionId = "343", operation = "FixedValueByMembersEnteringPerDay")
        val detailsCall = mockCall(PermitContentResponse(PermitContentPayload(rules = listOf(fallbackRule))))
        `when`(recreationApi.getPermitDetails("233261")).thenReturn(detailsCall)

        val result = cache.get("233261")

        assertEquals(listOf(fallbackRule), result?.rules)
        assertEquals(primaryPayload.divisions, result?.divisions)
    }

    @Test
    fun `does not call permit details fallback when permitcontent rules are already present`() {
        `when`(valueOps.get("permit:content:233261")).thenReturn(null)
        val payload = PermitContentPayload(rules = listOf(PermitRuleContent(operation = "FixedValueByMembersEnteringPerDay")))
        val call = mockCall(PermitContentResponse(payload))
        `when`(recreationApi.getPermitContent("233261")).thenReturn(call)

        cache.get("233261")

        verify(recreationApi, never()).getPermitDetails(anyString())
    }

    @Test
    fun `returns primary payload unchanged when the permit details fallback also fails`() {
        `when`(valueOps.get("permit:content:233261")).thenReturn(null)
        val primaryPayload = PermitContentPayload(divisions = mapOf("343" to PermitDivisionContent(id = "343")))
        val primaryCall = mockCall(PermitContentResponse(primaryPayload))
        `when`(recreationApi.getPermitContent("233261")).thenReturn(primaryCall)

        val detailsCall = mock(Call::class.java)
        `when`(detailsCall.execute()).thenThrow(RuntimeException("down"))
        @Suppress("UNCHECKED_CAST")
        `when`(recreationApi.getPermitDetails("233261")).thenReturn(detailsCall as Call<PermitContentResponse>)

        val result = cache.get("233261")

        assertEquals(primaryPayload, result)
    }
}
