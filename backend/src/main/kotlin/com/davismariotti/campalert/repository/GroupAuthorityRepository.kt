package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.GroupAuthority
import com.davismariotti.campalert.model.GroupAuthorityId
import org.springframework.data.repository.CrudRepository

interface GroupAuthorityRepository : CrudRepository<GroupAuthority, GroupAuthorityId> {
    fun findByIdGroupIdIn(groupIds: Collection<Long>): List<GroupAuthority>
}
