package com.davismariotti.campalert.service.sms

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.concurrent.TimeUnit

class SmsConversationServiceTest {
    @Suppress("UNCHECKED_CAST")
    private val redisTemplate = mock(StringRedisTemplate::class.java)

    @Suppress("UNCHECKED_CAST")
    private val valueOps = mock(ValueOperations::class.java) as ValueOperations<String, String>
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    private val service = SmsConversationService(redisTemplate, objectMapper)

    init {
        `when`(redisTemplate.opsForValue()).thenReturn(valueOps)
    }

    // --- sms:context ---

    @Test
    fun `getContext returns null when key missing`() {
        `when`(valueOps.get("sms:context:+15550001234")).thenReturn(null)
        assertNull(service.getContext("+15550001234"))
    }

    @Test
    fun `getContext returns request IDs from Redis`() {
        `when`(valueOps.get("sms:context:+15550001234"))
            .thenReturn("""{"requestIds":[1,2]}""")
        assertEquals(listOf(1, 2), service.getContext("+15550001234"))
    }

    @Test
    fun `setContext writes JSON with 24h TTL`() {
        service.setContext("+15550001234", listOf(5, 6))
        verify(valueOps).set(eq("sms:context:+15550001234"), any(String::class.java), eq(24L), eq(TimeUnit.HOURS))
    }

    // --- sms:awaiting ---

    @Test
    fun `getAwaiting returns null when key missing`() {
        `when`(valueOps.get("sms:awaiting:+15550001234")).thenReturn(null)
        assertNull(service.getAwaiting("+15550001234"))
    }

    @Test
    fun `getAwaiting deserializes AwaitingContext`() {
        `when`(valueOps.get("sms:awaiting:+15550001234"))
            .thenReturn("""{"intent":"PAUSE","requestIds":[3]}""")
        val ctx = service.getAwaiting("+15550001234")!!
        assertEquals("PAUSE", ctx.intent)
        assertEquals(listOf(3), ctx.requestIds)
    }

    @Test
    fun `setAwaiting writes JSON with 10min TTL`() {
        service.setAwaiting("+15550001234", "PAUSE", listOf(7))
        verify(valueOps).set(eq("sms:awaiting:+15550001234"), any(String::class.java), eq(10L), eq(TimeUnit.MINUTES))
    }

    @Test
    fun `clearAwaiting deletes the key`() {
        service.clearAwaiting("+15550001234")
        verify(redisTemplate).delete(eq("sms:awaiting:+15550001234"))
    }
}
