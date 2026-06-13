package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.SearchRequest
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.transaction.annotation.Transactional

interface SearchRequestRepository : CrudRepository<SearchRequest, Int> {
    fun findByCompletedFalse(): List<SearchRequest>

    fun findByCompleted(completed: Boolean): List<SearchRequest>

    fun findByUserId(userId: Long): List<SearchRequest>

    fun findByCompletedAndUserId(completed: Boolean, userId: Long): List<SearchRequest>

    fun findByUserIdAndCompletedFalseAndPauseReasonIsNull(userId: Long): List<SearchRequest>

    fun findByUserIdAndPauseReason(userId: Long, pauseReason: String): List<SearchRequest>

    @Modifying
    @Transactional
    @Query("UPDATE SearchRequest r SET r.campgroundTimezone = :timezone WHERE r.id = :id")
    fun updateTimezone(id: Int, timezone: String?)
}
