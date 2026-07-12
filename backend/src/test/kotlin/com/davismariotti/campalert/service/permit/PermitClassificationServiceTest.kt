package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.model.SearchType
import com.davismariotti.campalert.recreation.PermitContentPayload
import com.davismariotti.campalert.recreation.PermitDivisionContent
import com.davismariotti.campalert.recreation.PermitDivisionType
import com.davismariotti.campalert.recreation.PermitMappingPayload
import com.davismariotti.campalert.recreation.PermitMappingResponse
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

class PermitClassificationServiceTest {
    private val recreationApi = mock(RecreationApi::class.java)
    private val permitContentCache = mock(PermitContentCache::class.java)

    private val redisTemplate = mock(StringRedisTemplate::class.java)

    @Suppress("UNCHECKED_CAST")
    private val valueOps = mock(ValueOperations::class.java) as ValueOperations<String, String>
    private val objectMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    private val redisJsonCache = RedisJsonCache(redisTemplate, objectMapper)

    private val circuitBreakerRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
    private val retryRegistry = RetryRegistry.of(RetryConfig.ofDefaults())

    private val service = PermitClassificationService(
        recreationApi,
        permitContentCache,
        redisJsonCache,
        circuitBreakerRegistry,
        retryRegistry,
        24L,
    )

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

    private fun mapping(itinerary: List<String> = emptyList(), dayUse: List<String> = emptyList()) = PermitMappingPayload(itineraryPermitIds = itinerary, dayUsePermitIds = dayUse)

    /** Stubs getPermitMapping(); note: the Call must be built *before* opening the when(...) stub below,
     *  since mockCall() itself calls when(...) internally — nesting it would corrupt Mockito's stubbing state. */
    private fun stubMapping(payload: PermitMappingPayload) {
        val call = mockCall(PermitMappingResponse(payload))
        `when`(recreationApi.getPermitMapping()).thenReturn(call)
    }

    @Test
    fun `permit flagged in itinerary bucket classifies as ITINERARY`() {
        `when`(valueOps.get("permit:mapping")).thenReturn(null)
        stubMapping(mapping(itinerary = listOf("4675323")))

        val result = service.classify("4675323")

        assertEquals(SearchType.ITINERARY, result)
        verify(permitContentCache, never()).get(anyString())
    }

    @Test
    fun `permit flagged in an unsupported bucket classifies as unsupported`() {
        `when`(valueOps.get("permit:mapping")).thenReturn(null)
        stubMapping(mapping(dayUse = listOf("16851")))

        val result = service.classify("16851")

        assertNull(result)
        verify(permitContentCache, never()).get(anyString())
    }

    @Test
    fun `unflagged permit with valid zone structure classifies as ZONE`() {
        `when`(valueOps.get("permit:mapping")).thenReturn(null)
        stubMapping(mapping())
        `when`(permitContentCache.get("233261")).thenReturn(
            PermitContentPayload(
                divisions = mapOf("343" to PermitDivisionContent(id = "343", type = PermitDivisionType.DESTINATION_ZONE)),
                rules = listOf(PermitRuleContent(operation = "FixedValueByMembersEnteringPerDay")),
            ),
        )

        val result = service.classify("233261")

        assertEquals(SearchType.ZONE, result)
    }

    @Test
    fun `unflagged permit failing structural verification classifies as unsupported`() {
        `when`(valueOps.get("permit:mapping")).thenReturn(null)
        stubMapping(mapping())
        `when`(permitContentCache.get("999")).thenReturn(PermitContentPayload(divisions = emptyMap(), rules = emptyList()))

        val result = service.classify("999")

        assertNull(result)
    }

    @Test
    fun `unflagged permit with zone division but no matching rule classifies as unsupported`() {
        `when`(valueOps.get("permit:mapping")).thenReturn(null)
        stubMapping(mapping())
        `when`(permitContentCache.get("999")).thenReturn(
            PermitContentPayload(
                divisions = mapOf("1" to PermitDivisionContent(id = "1", type = PermitDivisionType.DESTINATION_ZONE)),
                rules = emptyList(),
            ),
        )

        assertNull(service.classify("999"))
    }

    @Test
    fun `mapping fetch failure fails closed to unsupported`() {
        `when`(valueOps.get("permit:mapping")).thenReturn(null)
        val call = mock(Call::class.java)
        `when`(call.execute()).thenThrow(RuntimeException("network down"))
        @Suppress("UNCHECKED_CAST")
        `when`(recreationApi.getPermitMapping()).thenReturn(call as Call<PermitMappingResponse>)

        assertNull(service.classify("233261"))
    }

    @Test
    fun `cached mapping is reused without refetching`() {
        `when`(valueOps.get("permit:mapping")).thenReturn(objectMapper.writeValueAsString(mapping(itinerary = listOf("4675323"))))

        val result = service.classify("4675323")

        assertEquals(SearchType.ITINERARY, result)
        verify(recreationApi, never()).getPermitMapping()
    }

    @Test
    fun `mapping fetch writes cache with configured TTL`() {
        `when`(valueOps.get("permit:mapping")).thenReturn(null)
        stubMapping(mapping(itinerary = listOf("4675323")))

        service.classify("4675323")

        verify(valueOps).set(eq("permit:mapping"), any(), eq(24L), eq(TimeUnit.HOURS))
    }
}
