package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.SearchRequestsApiDelegate
import com.davismariotti.campalert.api.model.CreateSearchRequestBody
import com.davismariotti.campalert.api.model.ErrorResponse
import com.davismariotti.campalert.api.model.SearchRequestResponse
import com.davismariotti.campalert.api.model.UpdateSearchRequestBody
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class SearchRequestsDelegateImpl(
    private val searchRequestRepository: SearchRequestRepository,
    private val userRepository: UserRepository,
    private val phoneNumberRepository: PhoneNumberRepository,
) : SearchRequestsApiDelegate {
    private fun currentUserId(): Long {
        val email = SecurityContextHolder.getContext().authentication.name
        return userRepository.findByEmail(email)!!.id!!
    }

    @PreAuthorize("isAuthenticated()")
    override fun listSearchRequests(completed: Boolean?): ResponseEntity<List<SearchRequestResponse>> {
        val userId = currentUserId()
        val results = if (completed != null) {
            searchRequestRepository.findByCompletedAndUserId(completed, userId)
        } else {
            searchRequestRepository.findByUserId(userId)
        }
        return ResponseEntity.ok(results.map { it.toResponse() })
    }

    @Suppress("UNCHECKED_CAST")
    @PreAuthorize("isAuthenticated()")
    override fun createSearchRequest(
        createSearchRequestBody: CreateSearchRequestBody,
    ): ResponseEntity<SearchRequestResponse> {
        val userId = currentUserId()
        if (phoneNumberRepository.countByUserIdAndStatus(userId, PhoneNumberStatus.VERIFIED) == 0L) {
            return ResponseEntity.status(422).body(
                ErrorResponse(
                    message = "A verified phone number is required to create a search request.",
                    code = "NO_VERIFIED_PHONE",
                ),
            ) as ResponseEntity<SearchRequestResponse>
        }
        val entity = SearchRequest(
            startDay = createSearchRequestBody.startDay,
            nights = createSearchRequestBody.nights,
            groupSize = createSearchRequestBody.groupSize,
            campsiteId = createSearchRequestBody.campsiteId,
            campgroundName = createSearchRequestBody.campgroundName,
            loops = createSearchRequestBody.loops,
            name = createSearchRequestBody.name,
            completed = false,
            userId = userId,
        )
        return ResponseEntity.status(201).body(searchRequestRepository.save(entity).toResponse())
    }

    @PreAuthorize("isAuthenticated()")
    override fun getSearchRequest(id: Int): ResponseEntity<SearchRequestResponse> {
        val userId = currentUserId()
        val entity = searchRequestRepository.findById(id).orElse(null)
            ?.takeIf { it.userId == userId }
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(entity.toResponse())
    }

    @PreAuthorize("isAuthenticated()")
    override fun updateSearchRequest(
        id: Int,
        updateSearchRequestBody: UpdateSearchRequestBody
    ): ResponseEntity<SearchRequestResponse> {
        val userId = currentUserId()
        val existing = searchRequestRepository.findById(id).orElse(null)
            ?.takeIf { it.userId == userId }
            ?: return ResponseEntity.notFound().build()
        val updated = existing.copy(
            startDay = updateSearchRequestBody.startDay,
            nights = updateSearchRequestBody.nights,
            groupSize = updateSearchRequestBody.groupSize,
            campsiteId = updateSearchRequestBody.campsiteId,
            loops = updateSearchRequestBody.loops,
            name = updateSearchRequestBody.name,
            completed = updateSearchRequestBody.completed,
        )
        return ResponseEntity.ok(searchRequestRepository.save(updated).toResponse())
    }

    @PreAuthorize("isAuthenticated()")
    override fun deleteSearchRequest(id: Int): ResponseEntity<Unit> {
        val userId = currentUserId()
        val entity = searchRequestRepository.findById(id).orElse(null)
            ?.takeIf { it.userId == userId }
            ?: return ResponseEntity.notFound().build()
        searchRequestRepository.deleteById(entity.id!!)
        return ResponseEntity.noContent().build()
    }

    private fun SearchRequest.toResponse() =
        SearchRequestResponse(
            id = this.id!!,
            startDay = this.startDay,
            nights = this.nights,
            groupSize = this.groupSize,
            campsiteId = this.campsiteId,
            campgroundName = this.campgroundName,
            loops = this.loops,
            name = this.name,
            completed = this.completed,
            pauseReason = this.pauseReason,
        )
}
