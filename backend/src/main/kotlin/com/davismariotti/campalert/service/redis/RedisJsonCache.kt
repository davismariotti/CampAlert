package com.davismariotti.campalert.service.redis

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.TimeUnit

/**
 * Wraps the get -> readValue / writeValueAsString -> set(ttl) round trip that every Redis-backed
 * cache in this service otherwise duplicates by hand, including its own key-building and
 * ObjectMapper calls. Call sites still own their key strings and TTLs; this only removes the
 * repeated (de)serialization boilerplate around them.
 */
@Component
class RedisJsonCache(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    fun <T : Any> get(key: String, type: Class<T>): T? = redisTemplate.opsForValue().get(key)?.let { objectMapper.readValue(it, type) }

    fun <T : Any> get(key: String, type: TypeReference<T>): T? = redisTemplate.opsForValue().get(key)?.let { objectMapper.readValue(it, type) }

    fun set(
        key: String,
        value: Any,
        ttl: Long,
        unit: TimeUnit
    ) {
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl, unit)
    }

    fun delete(key: String) {
        redisTemplate.delete(key)
    }

    /** Cache-aside: returns the cached value under [key] if present, otherwise calls [loader], caches a non-null result with [ttl]/[unit], and returns it. */
    fun <T : Any> getOrLoad(
        key: String,
        type: Class<T>,
        ttl: Long,
        unit: TimeUnit,
        loader: () -> T?
    ): T? {
        get(key, type)?.let { return it }
        val loaded = loader() ?: return null
        set(key, loaded, ttl, unit)
        return loaded
    }
}
