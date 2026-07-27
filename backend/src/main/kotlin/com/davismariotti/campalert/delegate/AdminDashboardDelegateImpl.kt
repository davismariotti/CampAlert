package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.AdminDashboardApiDelegate
import com.davismariotti.campalert.api.model.AdminRequestCountByProvider
import com.davismariotti.campalert.api.model.AdminStatsResponse
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.repository.PermitSearchRequestRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class AdminDashboardDelegateImpl(
    private val userRepository: UserRepository,
    private val searchRequestRepository: SearchRequestRepository,
    private val permitSearchRequestRepository: PermitSearchRequestRepository,
) : AdminDashboardApiDelegate {
    @PreAuthorize("hasAuthority('VIEW_ADMIN_DASHBOARD')")
    override fun adminGetStats(): ResponseEntity<AdminStatsResponse> {
        val activeThreshold = Instant.now().minus(30, ChronoUnit.DAYS)
        val requestCounts = Provider.entries.map { provider ->
            AdminRequestCountByProvider(
                provider.toApiType(),
                searchRequestRepository.countByProvider(provider),
                permitSearchRequestRepository.countByProvider(provider),
            )
        }
        return ResponseEntity.ok(
            AdminStatsResponse(
                totalUsers = userRepository.count(),
                activeUsersLast30Days = userRepository.countByLastLoginAtAfter(activeThreshold),
                requestCounts = requestCounts,
            ),
        )
    }
}
