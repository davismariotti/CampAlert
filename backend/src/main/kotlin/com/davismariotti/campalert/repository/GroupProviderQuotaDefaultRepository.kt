package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.GroupProviderQuotaDefault
import com.davismariotti.campalert.model.GroupProviderQuotaDefaultId
import com.davismariotti.campalert.provider.Provider
import org.springframework.data.repository.CrudRepository

interface GroupProviderQuotaDefaultRepository : CrudRepository<GroupProviderQuotaDefault, GroupProviderQuotaDefaultId> {
    fun findByIdGroupIdInAndIdProvider(groupIds: Collection<Long>, provider: Provider): List<GroupProviderQuotaDefault>

    fun findByIdGroupIdIn(groupIds: Collection<Long>): List<GroupProviderQuotaDefault>
}
