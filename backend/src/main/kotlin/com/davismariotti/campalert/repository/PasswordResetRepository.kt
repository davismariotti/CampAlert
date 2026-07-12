package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.PasswordReset
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface PasswordResetRepository : CrudRepository<PasswordReset, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pr FROM PasswordReset pr WHERE pr.id = :id AND pr.consumedAt IS NULL")
    fun findPendingByIdForUpdate(id: UUID): PasswordReset?

    @Query("SELECT pr FROM PasswordReset pr WHERE pr.userId = :userId ORDER BY pr.createdAt DESC LIMIT 1")
    fun findLatestByUserId(userId: Long): PasswordReset?

    @Modifying
    @Transactional
    @Query("UPDATE PasswordReset pr SET pr.consumedAt = :now WHERE pr.userId = :userId AND pr.consumedAt IS NULL")
    fun consumeAllPendingByUserId(userId: Long, now: Instant): Int

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM PasswordReset pr WHERE pr.expiresAt < :cutoff")
    fun deleteExpiredBefore(cutoff: Instant): Int
}
