package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.PermitSearchRequest
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface PermitSearchRequestRepository : CrudRepository<PermitSearchRequest, Long> {
    @Query("SELECT r FROM PermitSearchRequest r WHERE r.state.completed = false")
    fun findAllIncomplete(): List<PermitSearchRequest>

    fun findByUserId(userId: Long): List<PermitSearchRequest>

    @Query("SELECT r FROM PermitSearchRequest r WHERE r.state.completed = :completed AND r.userId = :userId")
    fun findByCompletedAndUserId(completed: Boolean, userId: Long): List<PermitSearchRequest>
}
