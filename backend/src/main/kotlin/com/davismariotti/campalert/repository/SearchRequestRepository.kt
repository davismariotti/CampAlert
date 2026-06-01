package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.SearchRequest
import org.springframework.data.repository.CrudRepository

interface SearchRequestRepository : CrudRepository<SearchRequest, Int> {
    fun findByCompletedFalse(): List<SearchRequest>

    fun findByCompleted(completed: Boolean): List<SearchRequest>

    fun findByUserId(userId: Long): List<SearchRequest>

    fun findByCompletedAndUserId(completed: Boolean, userId: Long): List<SearchRequest>
}
