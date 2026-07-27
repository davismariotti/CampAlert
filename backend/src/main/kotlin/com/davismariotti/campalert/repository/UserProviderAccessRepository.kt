package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.UserProviderAccess
import com.davismariotti.campalert.model.UserProviderAccessId
import com.davismariotti.campalert.provider.Provider
import org.springframework.data.repository.CrudRepository

interface UserProviderAccessRepository : CrudRepository<UserProviderAccess, UserProviderAccessId> {
    fun findByIdUserId(userId: Long): List<UserProviderAccess>

    fun findByIdUserIdAndIdProvider(userId: Long, provider: Provider): UserProviderAccess?

    fun deleteByIdUserIdAndIdProvider(userId: Long, provider: Provider)
}
