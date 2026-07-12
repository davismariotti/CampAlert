package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.PermitSearchRequest
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface PermitSearchRequestRepository : CrudRepository<PermitSearchRequest, Long> {
    @Query("SELECT r FROM PermitSearchRequest r WHERE r.state.completed = false")
    fun findAllIncomplete(): List<PermitSearchRequest>

    @Query("SELECT r FROM PermitSearchRequest r WHERE r.permitId = :permitId AND r.state.completed = false")
    fun findByPermitIdAndCompletedFalse(permitId: String): List<PermitSearchRequest>

    @Query(
        "SELECT DISTINCT r.permitId FROM PermitSearchRequest r WHERE r.state.completed = false AND r.state.pauseReason IS NULL AND r.userId IS NOT NULL",
    )
    fun findDistinctActivePermitIds(): List<String>

    fun findByUserId(userId: Long): List<PermitSearchRequest>

    @Query("SELECT r FROM PermitSearchRequest r WHERE r.state.completed = :completed AND r.userId = :userId")
    fun findByCompletedAndUserId(completed: Boolean, userId: Long): List<PermitSearchRequest>
}
