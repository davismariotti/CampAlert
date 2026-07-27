package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.provider.Provider
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.transaction.annotation.Transactional

data class ActiveCampsiteTarget(
    val campsiteId: Int,
    val provider: Provider
)

interface SearchRequestRepository : CrudRepository<SearchRequest, Long> {
    @Query("SELECT r FROM SearchRequest r WHERE r.state.completed = false AND r.deletedAt IS NULL")
    fun findAllIncomplete(): List<SearchRequest>

    @Query("SELECT r FROM SearchRequest r WHERE r.campsiteId = :campsiteId AND r.provider = :provider AND r.state.completed = false AND r.deletedAt IS NULL")
    fun findByCampsiteIdAndProviderAndCompletedFalse(campsiteId: Int, provider: Provider): List<SearchRequest>

    @Query(
        "SELECT DISTINCT new com.davismariotti.campalert.repository.ActiveCampsiteTarget(r.campsiteId, r.provider) " +
            "FROM SearchRequest r WHERE r.state.completed = false AND r.state.pauseReason IS NULL AND r.userId IS NOT NULL AND r.deletedAt IS NULL",
    )
    fun findDistinctActiveCampsiteTargets(): List<ActiveCampsiteTarget>

    @Query("SELECT r FROM SearchRequest r WHERE r.state.completed = :completed AND r.deletedAt IS NULL")
    fun findAllByCompleted(completed: Boolean): List<SearchRequest>

    @Query("SELECT r FROM SearchRequest r WHERE r.userId = :userId AND r.deletedAt IS NULL")
    fun findByUserId(userId: Long): List<SearchRequest>

    @Query("SELECT r FROM SearchRequest r WHERE r.state.completed = :completed AND r.userId = :userId AND r.deletedAt IS NULL")
    fun findByCompletedAndUserId(completed: Boolean, userId: Long): List<SearchRequest>

    @Query("SELECT r FROM SearchRequest r WHERE r.userId = :userId AND r.deletedAt IS NOT NULL")
    fun findDeletedByUserId(userId: Long): List<SearchRequest>

    @Query("SELECT r FROM SearchRequest r WHERE r.state.completed = :completed AND r.userId = :userId AND r.deletedAt IS NOT NULL")
    fun findDeletedByCompletedAndUserId(completed: Boolean, userId: Long): List<SearchRequest>

    @Query("SELECT r FROM SearchRequest r WHERE r.userId = :userId AND r.state.completed = false AND r.state.pauseReason IS NULL AND r.deletedAt IS NULL")
    fun findActiveUnpausedByUserId(userId: Long): List<SearchRequest>

    @Query("SELECT r FROM SearchRequest r WHERE r.userId = :userId AND r.state.pauseReason = :pauseReason AND r.deletedAt IS NULL")
    fun findByUserIdAndPauseReason(userId: Long, pauseReason: String): List<SearchRequest>

    @Query("SELECT r FROM SearchRequest r WHERE r.userId = :userId AND r.state.completed = false AND r.deletedAt IS NULL")
    fun findActiveByUserId(userId: Long): List<SearchRequest>

    @Query("SELECT COUNT(r) FROM SearchRequest r WHERE r.userId = :userId AND r.state.completed = false AND r.deletedAt IS NULL")
    fun countActiveByUserId(userId: Long): Long

    @Query("SELECT COUNT(r) FROM SearchRequest r WHERE r.userId = :userId AND r.provider = :provider AND r.state.completed = false AND r.deletedAt IS NULL")
    fun countActiveByUserIdAndProvider(userId: Long, provider: Provider): Long

    fun countByProvider(provider: Provider): Long

    @Modifying
    @Transactional
    @Query("UPDATE SearchRequest r SET r.campgroundTimezone = :timezone WHERE r.id = :id")
    fun updateTimezone(id: Long, timezone: String?)
}
