package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.Provider
import com.davismariotti.campalert.model.SearchRequest
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.transaction.annotation.Transactional

data class ActiveCampsiteTarget(
    val campsiteId: Int,
    val provider: Provider
)

interface SearchRequestRepository : CrudRepository<SearchRequest, Long> {
    @Query("SELECT r FROM SearchRequest r WHERE r.state.completed = false")
    fun findAllIncomplete(): List<SearchRequest>

    @Query("SELECT r FROM SearchRequest r WHERE r.campsiteId = :campsiteId AND r.provider = :provider AND r.state.completed = false")
    fun findByCampsiteIdAndProviderAndCompletedFalse(campsiteId: Int, provider: Provider): List<SearchRequest>

    @Query(
        "SELECT DISTINCT new com.davismariotti.campalert.repository.ActiveCampsiteTarget(r.campsiteId, r.provider) " +
            "FROM SearchRequest r WHERE r.state.completed = false AND r.state.pauseReason IS NULL AND r.userId IS NOT NULL",
    )
    fun findDistinctActiveCampsiteTargets(): List<ActiveCampsiteTarget>

    @Query("SELECT r FROM SearchRequest r WHERE r.state.completed = :completed")
    fun findAllByCompleted(completed: Boolean): List<SearchRequest>

    fun findByUserId(userId: Long): List<SearchRequest>

    @Query("SELECT r FROM SearchRequest r WHERE r.state.completed = :completed AND r.userId = :userId")
    fun findByCompletedAndUserId(completed: Boolean, userId: Long): List<SearchRequest>

    @Query("SELECT r FROM SearchRequest r WHERE r.userId = :userId AND r.state.completed = false AND r.state.pauseReason IS NULL")
    fun findActiveUnpausedByUserId(userId: Long): List<SearchRequest>

    @Query("SELECT r FROM SearchRequest r WHERE r.userId = :userId AND r.state.pauseReason = :pauseReason")
    fun findByUserIdAndPauseReason(userId: Long, pauseReason: String): List<SearchRequest>

    @Modifying
    @Transactional
    @Query("UPDATE SearchRequest r SET r.campgroundTimezone = :timezone WHERE r.id = :id")
    fun updateTimezone(id: Long, timezone: String?)
}
