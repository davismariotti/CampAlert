package com.davismariotti.campfinder.delegate

import com.davismariotti.campfinder.api.SearchRequestsApiDelegate
import com.davismariotti.campfinder.api.model.CreateSearchRequestBody
import com.davismariotti.campfinder.api.model.SearchRequestResponse
import com.davismariotti.campfinder.api.model.UpdateSearchRequestBody
import com.davismariotti.campfinder.model.SearchRequest
import com.davismariotti.campfinder.repository.SearchRequestRepository
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service

@Service
class SearchRequestsDelegateImpl(
    private val searchRequestRepository: SearchRequestRepository
) : SearchRequestsApiDelegate {

    override fun listSearchRequests(completed: Boolean?): ResponseEntity<List<SearchRequestResponse>> {
        val results = if (completed != null) {
            searchRequestRepository.findByCompleted(completed)
        } else {
            searchRequestRepository.findAll().toList()
        }
        return ResponseEntity.ok(results.map { it.toResponse() })
    }

    override fun createSearchRequest(createSearchRequestBody: CreateSearchRequestBody): ResponseEntity<SearchRequestResponse> {
        val entity = SearchRequest(
            startDay = createSearchRequestBody.startDay,
            nights = createSearchRequestBody.nights,
            groupSize = createSearchRequestBody.groupSize,
            campsiteId = createSearchRequestBody.campsiteId,
            loops = createSearchRequestBody.loops,
            name = createSearchRequestBody.name,
            completed = false
        )
        return ResponseEntity.status(201).body(searchRequestRepository.save(entity).toResponse())
    }

    override fun getSearchRequest(id: Int): ResponseEntity<SearchRequestResponse> {
        val entity = searchRequestRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(entity.toResponse())
    }

    override fun updateSearchRequest(id: Int, updateSearchRequestBody: UpdateSearchRequestBody): ResponseEntity<SearchRequestResponse> {
        val existing = searchRequestRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val updated = existing.copy(
            startDay = updateSearchRequestBody.startDay,
            nights = updateSearchRequestBody.nights,
            groupSize = updateSearchRequestBody.groupSize,
            campsiteId = updateSearchRequestBody.campsiteId,
            loops = updateSearchRequestBody.loops,
            name = updateSearchRequestBody.name,
            completed = updateSearchRequestBody.completed
        )
        return ResponseEntity.ok(searchRequestRepository.save(updated).toResponse())
    }

    override fun deleteSearchRequest(id: Int): ResponseEntity<Unit> {
        if (!searchRequestRepository.existsById(id)) return ResponseEntity.notFound().build()
        searchRequestRepository.deleteById(id)
        return ResponseEntity.noContent().build()
    }

    private fun SearchRequest.toResponse() = SearchRequestResponse(
        id = this.id!!,
        startDay = this.startDay,
        nights = this.nights,
        groupSize = this.groupSize,
        campsiteId = this.campsiteId,
        loops = this.loops,
        name = this.name,
        completed = this.completed
    )
}
