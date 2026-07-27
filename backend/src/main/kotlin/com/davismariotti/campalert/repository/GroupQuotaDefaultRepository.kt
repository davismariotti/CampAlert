package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.GroupQuotaDefault
import org.springframework.data.repository.CrudRepository

interface GroupQuotaDefaultRepository : CrudRepository<GroupQuotaDefault, Long> {
    fun findByGroupIdIn(groupIds: Collection<Long>): List<GroupQuotaDefault>
}
