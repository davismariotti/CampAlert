package com.davismariotti.campalert.service.sms

import com.davismariotti.campalert.service.redis.RedisJsonCache
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import tools.jackson.core.type.TypeReference
import java.util.concurrent.TimeUnit

class SmsConversationServiceTest {
    private val redisJsonCache = mock(RedisJsonCache::class.java)
    private val service = SmsConversationService(redisJsonCache)

    // --- sms:context ---

    @Test
    fun `getContext returns null when key missing`() {
        `when`(redisJsonCache.get(eq("sms:context:+15550001234"), any<TypeReference<Map<String, List<Long>>>>())).thenReturn(null)
        assertNull(service.getContext("+15550001234"))
    }

    @Test
    fun `getContext returns request IDs from Redis`() {
        `when`(redisJsonCache.get(eq("sms:context:+15550001234"), any<TypeReference<Map<String, List<Long>>>>()))
            .thenReturn(mapOf("requestIds" to listOf(1L, 2L)))
        assertEquals(listOf(1L, 2L), service.getContext("+15550001234"))
    }

    @Test
    fun `setContext writes JSON with 24h TTL`() {
        service.setContext("+15550001234", listOf(5, 6))
        verify(redisJsonCache).set(eq("sms:context:+15550001234"), any(), eq(24L), eq(TimeUnit.HOURS))
    }

    // --- sms:awaiting ---

    @Test
    fun `getAwaiting returns null when key missing`() {
        `when`(redisJsonCache.get("sms:awaiting:+15550001234", AwaitingContext::class.java)).thenReturn(null)
        assertNull(service.getAwaiting("+15550001234"))
    }

    @Test
    fun `getAwaiting deserializes AwaitingContext`() {
        `when`(redisJsonCache.get("sms:awaiting:+15550001234", AwaitingContext::class.java))
            .thenReturn(AwaitingContext(intent = "PAUSE", requestIds = listOf(3)))
        val ctx = service.getAwaiting("+15550001234")!!
        assertEquals("PAUSE", ctx.intent)
        assertEquals(listOf(3L), ctx.requestIds)
    }

    @Test
    fun `setAwaiting writes JSON with 10min TTL`() {
        service.setAwaiting("+15550001234", "PAUSE", listOf(7))
        verify(redisJsonCache).set(eq("sms:awaiting:+15550001234"), any(), eq(10L), eq(TimeUnit.MINUTES))
    }

    @Test
    fun `clearAwaiting deletes the key`() {
        service.clearAwaiting("+15550001234")
        verify(redisJsonCache).delete(eq("sms:awaiting:+15550001234"))
    }
}
