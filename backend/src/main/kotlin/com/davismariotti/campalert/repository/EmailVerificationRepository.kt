package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.EmailVerification
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface EmailVerificationRepository : CrudRepository<EmailVerification, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ev FROM EmailVerification ev WHERE ev.id = :id AND ev.consumedAt IS NULL")
    fun findPendingByIdForUpdate(id: UUID): EmailVerification?

    @Query("SELECT ev FROM EmailVerification ev WHERE ev.userId = :userId ORDER BY ev.createdAt DESC LIMIT 1")
    fun findLatestByUserId(userId: Long): EmailVerification?

    @Modifying
    @Transactional
    @Query("UPDATE EmailVerification ev SET ev.consumedAt = :now WHERE ev.userId = :userId AND ev.consumedAt IS NULL")
    fun consumeAllPendingByUserId(userId: Long, now: Instant): Int
}
