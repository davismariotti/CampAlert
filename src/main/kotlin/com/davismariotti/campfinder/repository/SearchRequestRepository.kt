package com.davismariotti.campfinder.repository

import com.davismariotti.campfinder.model.SearchRequest
import org.springframework.data.repository.CrudRepository

interface SearchRequestRepository : CrudRepository<SearchRequest, Int> {
    fun findByCompletedFalse(): List<SearchRequest>
    fun findByCompleted(completed: Boolean): List<SearchRequest>
}