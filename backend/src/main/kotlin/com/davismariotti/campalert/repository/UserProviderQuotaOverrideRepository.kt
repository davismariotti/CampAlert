package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.UserProviderQuotaOverride
import com.davismariotti.campalert.model.UserProviderQuotaOverrideId
import com.davismariotti.campalert.provider.Provider
import org.springframework.data.repository.CrudRepository

interface UserProviderQuotaOverrideRepository : CrudRepository<UserProviderQuotaOverride, UserProviderQuotaOverrideId> {
    fun findByIdUserId(userId: Long): List<UserProviderQuotaOverride>

    fun findByIdUserIdAndIdProvider(userId: Long, provider: Provider): UserProviderQuotaOverride?

    fun deleteByIdUserIdAndIdProvider(userId: Long, provider: Provider)
}
