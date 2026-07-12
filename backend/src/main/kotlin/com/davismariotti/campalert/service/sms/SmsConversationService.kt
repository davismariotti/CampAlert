package com.davismariotti.campalert.service.sms

import com.davismariotti.campalert.service.redis.RedisJsonCache
import org.springframework.stereotype.Service
import tools.jackson.core.type.TypeReference
import java.util.concurrent.TimeUnit

@Service
class SmsConversationService(
    private val redisJsonCache: RedisJsonCache,
) {
    fun getContext(phone: String): List<Long>? {
        val map = redisJsonCache.get(contextKey(phone), object : TypeReference<Map<String, List<Long>>>() {})
        return map?.get("requestIds")
    }

    fun setContext(phone: String, requestIds: List<Long>) {
        redisJsonCache.set(contextKey(phone), mapOf("requestIds" to requestIds), 24, TimeUnit.HOURS)
    }

    fun setAwaiting(phone: String, intent: String, requestIds: List<Long>) {
        redisJsonCache.set(awaitingKey(phone), AwaitingContext(intent = intent, requestIds = requestIds), 10, TimeUnit.MINUTES)
    }

    fun getAwaiting(phone: String): AwaitingContext? = redisJsonCache.get(awaitingKey(phone), AwaitingContext::class.java)

    fun clearAwaiting(phone: String) {
        redisJsonCache.delete(awaitingKey(phone))
    }

    private fun contextKey(phone: String) = "sms:context:$phone"

    private fun awaitingKey(phone: String) = "sms:awaiting:$phone"
}
