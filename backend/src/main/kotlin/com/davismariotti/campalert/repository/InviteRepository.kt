package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.Invite
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface InviteRepository : JpaRepository<Invite, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invite i WHERE i.id = :id")
    fun findByIdForUpdate(id: UUID): Invite?

    @Query(
        "SELECT i FROM Invite i WHERE :includeInactive = true OR " +
            "(i.deactivatedAt IS NULL AND i.usedCount < i.maxUses AND i.expiresAt > :now) " +
            "ORDER BY i.createdAt DESC",
    )
    fun findAllFiltered(includeInactive: Boolean, now: Instant, pageable: Pageable): Page<Invite>
}
