package com.davismariotti.campalert.service

import org.springframework.session.data.redis.RedisIndexedSessionRepository
import org.springframework.stereotype.Service

@Service
class SessionRevocationService(
    private val sessionRepository: RedisIndexedSessionRepository,
) {
    fun revokeAllSessionsFor(username: String) {
        sessionRepository.findByPrincipalName(username).keys.forEach { sessionId ->
            sessionRepository.deleteById(sessionId)
        }
    }
}
