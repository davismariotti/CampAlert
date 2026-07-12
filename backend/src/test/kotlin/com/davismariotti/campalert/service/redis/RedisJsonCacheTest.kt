package com.davismariotti.campalert.service.redis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.util.concurrent.TimeUnit

data class Widget(
    val name: String
)

class RedisJsonCacheTest {
    private val redisTemplate = mock(StringRedisTemplate::class.java)

    @Suppress("UNCHECKED_CAST")
    private val valueOps = mock(ValueOperations::class.java) as ValueOperations<String, String>
    private val objectMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    private val cache = RedisJsonCache(redisTemplate, objectMapper)

    @BeforeEach
    fun setUp() {
        `when`(redisTemplate.opsForValue()).thenReturn(valueOps)
    }

    @Test
    fun `get returns null on miss`() {
        `when`(valueOps.get("widget:1")).thenReturn(null)
        assertNull(cache.get("widget:1", Widget::class.java))
    }

    @Test
    fun `get deserializes a cached value`() {
        `when`(valueOps.get("widget:1")).thenReturn("""{"name":"gizmo"}""")
        assertEquals(Widget("gizmo"), cache.get("widget:1", Widget::class.java))
    }

    @Test
    fun `set serializes and writes with the given TTL`() {
        cache.set("widget:1", Widget("gizmo"), 5, TimeUnit.MINUTES)
        verify(valueOps).set(eq("widget:1"), eq("""{"name":"gizmo"}"""), eq(5L), eq(TimeUnit.MINUTES))
    }

    @Test
    fun `delete removes the key`() {
        cache.delete("widget:1")
        verify(redisTemplate).delete("widget:1")
    }

    @Test
    fun `getOrLoad returns the cached value without invoking the loader`() {
        `when`(valueOps.get("widget:1")).thenReturn("""{"name":"gizmo"}""")

        val result = cache.getOrLoad("widget:1", Widget::class.java, 5, TimeUnit.MINUTES) { Widget("should-not-load") }

        assertEquals(Widget("gizmo"), result)
        verify(valueOps, never()).set(eq("widget:1"), org.mockito.ArgumentMatchers.anyString(), eq(5L), eq(TimeUnit.MINUTES))
    }

    @Test
    fun `getOrLoad falls back to the loader on a miss and caches the result`() {
        `when`(valueOps.get("widget:1")).thenReturn(null)

        val result = cache.getOrLoad("widget:1", Widget::class.java, 5, TimeUnit.MINUTES) { Widget("gizmo") }

        assertEquals(Widget("gizmo"), result)
        verify(valueOps).set(eq("widget:1"), eq("""{"name":"gizmo"}"""), eq(5L), eq(TimeUnit.MINUTES))
    }

    @Test
    fun `getOrLoad returns null and skips the write when the loader returns null`() {
        `when`(valueOps.get("widget:1")).thenReturn(null)

        val result = cache.getOrLoad("widget:1", Widget::class.java, 5, TimeUnit.MINUTES) { null }

        assertNull(result)
        verify(valueOps, never()).set(eq("widget:1"), org.mockito.ArgumentMatchers.anyString(), eq(5L), eq(TimeUnit.MINUTES))
    }
}
