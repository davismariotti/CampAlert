package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.PermitSearchRequest
import com.davismariotti.campalert.provider.Provider
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

data class ActivePermitTarget(
    val permitId: String,
    val provider: Provider
)

interface PermitSearchRequestRepository : CrudRepository<PermitSearchRequest, Long> {
    @Query("SELECT r FROM PermitSearchRequest r WHERE r.state.completed = false")
    fun findAllIncomplete(): List<PermitSearchRequest>

    @Query("SELECT r FROM PermitSearchRequest r WHERE r.permitId = :permitId AND r.provider = :provider AND r.state.completed = false")
    fun findByPermitIdAndProviderAndCompletedFalse(permitId: String, provider: Provider): List<PermitSearchRequest>

    @Query(
        "SELECT DISTINCT new com.davismariotti.campalert.repository.ActivePermitTarget(r.permitId, r.provider) " +
            "FROM PermitSearchRequest r WHERE r.state.completed = false AND r.state.pauseReason IS NULL AND r.userId IS NOT NULL",
    )
    fun findDistinctActivePermitTargets(): List<ActivePermitTarget>

    fun findByUserId(userId: Long): List<PermitSearchRequest>

    @Query("SELECT r FROM PermitSearchRequest r WHERE r.state.completed = :completed AND r.userId = :userId")
    fun findByCompletedAndUserId(completed: Boolean, userId: Long): List<PermitSearchRequest>
}
