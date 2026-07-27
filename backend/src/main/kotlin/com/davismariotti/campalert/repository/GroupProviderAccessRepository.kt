package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.GroupProviderAccess
import com.davismariotti.campalert.model.GroupProviderAccessId
import com.davismariotti.campalert.provider.Provider
import org.springframework.data.repository.CrudRepository

interface GroupProviderAccessRepository : CrudRepository<GroupProviderAccess, GroupProviderAccessId> {
    fun findByIdGroupIdInAndIdProvider(groupIds: Collection<Long>, provider: Provider): List<GroupProviderAccess>

    fun findByIdGroupIdIn(groupIds: Collection<Long>): List<GroupProviderAccess>
}
