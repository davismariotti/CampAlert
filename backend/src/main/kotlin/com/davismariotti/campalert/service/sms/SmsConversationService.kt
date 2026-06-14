package com.davismariotti.campalert.service.sms

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.TimeUnit

@Service
class SmsConversationService(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    fun getContext(phone: String): List<Int>? {
        val json = redisTemplate.opsForValue().get(contextKey(phone)) ?: return null
        val map = objectMapper.readValue(json, object : TypeReference<Map<String, List<Int>>>() {})
        return map["requestIds"]
    }

    fun setContext(phone: String, requestIds: List<Int>) {
        val json = objectMapper.writeValueAsString(mapOf("requestIds" to requestIds))
        redisTemplate.opsForValue().set(contextKey(phone), json, 24, TimeUnit.HOURS)
    }

    fun setAwaiting(phone: String, intent: String, requestIds: List<Int>) {
        val json = objectMapper.writeValueAsString(AwaitingContext(intent = intent, requestIds = requestIds))
        redisTemplate.opsForValue().set(awaitingKey(phone), json, 10, TimeUnit.MINUTES)
    }

    fun getAwaiting(phone: String): AwaitingContext? {
        val json = redisTemplate.opsForValue().get(awaitingKey(phone)) ?: return null
        return objectMapper.readValue(json, AwaitingContext::class.java)
    }

    fun clearAwaiting(phone: String) {
        redisTemplate.delete(awaitingKey(phone))
    }

    private fun contextKey(phone: String) = "sms:context:$phone"

    private fun awaitingKey(phone: String) = "sms:awaiting:$phone"
}
