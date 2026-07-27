package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.AdminUserRequestsApiDelegate
import com.davismariotti.campalert.api.model.PermitSearchRequestResponse
import com.davismariotti.campalert.api.model.SearchRequestResponse
import com.davismariotti.campalert.api.model.UpdatePermitSearchRequestBody
import com.davismariotti.campalert.api.model.UpdateSearchRequestBody
import com.davismariotti.campalert.exception.NotFoundException
import com.davismariotti.campalert.model.AdminAuditAction
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.AdminAuditService
import com.davismariotti.campalert.util.currentUserId
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// Reuses the exact user-facing delegate code paths (via the ...As(userId, ...) methods) so admin
// edits go through the same, already-tested logic instead of duplicating it.
@Service
class AdminUserRequestsDelegateImpl(
    private val userRepository: UserRepository,
    private val adminAuditService: AdminAuditService,
    private val searchRequestsDelegateImpl: SearchRequestsDelegateImpl,
    private val permitSearchRequestsDelegateImpl: PermitSearchRequestsDelegateImpl,
) : AdminUserRequestsApiDelegate {
    private fun currentUserId(): Long = currentUserId(userRepository)

    private fun requireUser(id: Long) = userRepository.findById(id).orElse(null) ?: throw NotFoundException("User not found")

    @PreAuthorize("hasAuthority('MANAGE_USER_SEARCH_REQUESTS')")
    override fun adminListUserSearchRequests(id: Long, completed: Boolean?, deleted: Boolean): ResponseEntity<List<SearchRequestResponse>> {
        requireUser(id)
        return searchRequestsDelegateImpl.listSearchRequestsAs(id, completed, deleted)
    }

    @PreAuthorize("hasAuthority('MANAGE_USER_SEARCH_REQUESTS')")
    override fun adminUpdateUserSearchRequest(id: Long, requestId: Long, updateSearchRequestBody: UpdateSearchRequestBody): ResponseEntity<SearchRequestResponse> {
        requireUser(id)
        val response = searchRequestsDelegateImpl.updateSearchRequestAs(id, requestId, updateSearchRequestBody)
        adminAuditService.record(currentUserId(), id, AdminAuditAction.SEARCH_REQUEST_EDITED, "requestId=$requestId")
        return response
    }

    @Transactional
    @PreAuthorize("hasAuthority('MANAGE_USER_SEARCH_REQUESTS')")
    override fun adminDeleteUserSearchRequest(id: Long, requestId: Long): ResponseEntity<Unit> {
        requireUser(id)
        val response = searchRequestsDelegateImpl.deleteSearchRequestAs(id, requestId)
        adminAuditService.record(currentUserId(), id, AdminAuditAction.SEARCH_REQUEST_DELETED, "requestId=$requestId")
        return response
    }

    @PreAuthorize("hasAuthority('MANAGE_USER_SEARCH_REQUESTS')")
    override fun adminListUserPermitSearchRequests(id: Long, completed: Boolean?, deleted: Boolean): ResponseEntity<List<PermitSearchRequestResponse>> {
        requireUser(id)
        return permitSearchRequestsDelegateImpl.listPermitSearchRequestsAs(id, completed, deleted)
    }

    @PreAuthorize("hasAuthority('MANAGE_USER_SEARCH_REQUESTS')")
    override fun adminUpdateUserPermitSearchRequest(
        id: Long,
        requestId: Long,
        updatePermitSearchRequestBody: UpdatePermitSearchRequestBody,
    ): ResponseEntity<PermitSearchRequestResponse> {
        requireUser(id)
        val response = permitSearchRequestsDelegateImpl.updatePermitSearchRequestAs(id, requestId, updatePermitSearchRequestBody)
        adminAuditService.record(currentUserId(), id, AdminAuditAction.SEARCH_REQUEST_EDITED, "permitRequestId=$requestId")
        return response
    }

    @Transactional
    @PreAuthorize("hasAuthority('MANAGE_USER_SEARCH_REQUESTS')")
    override fun adminDeleteUserPermitSearchRequest(id: Long, requestId: Long): ResponseEntity<Unit> {
        requireUser(id)
        val response = permitSearchRequestsDelegateImpl.deletePermitSearchRequestAs(id, requestId)
        adminAuditService.record(currentUserId(), id, AdminAuditAction.SEARCH_REQUEST_DELETED, "permitRequestId=$requestId")
        return response
    }
}
