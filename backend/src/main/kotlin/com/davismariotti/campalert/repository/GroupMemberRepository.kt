package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.GroupMember
import com.davismariotti.campalert.model.GroupMemberId
import org.springframework.data.repository.CrudRepository

interface GroupMemberRepository : CrudRepository<GroupMember, GroupMemberId> {
    fun findByIdUserId(userId: Long): List<GroupMember>

    fun existsByIdUserIdAndIdGroupId(userId: Long, groupId: Long): Boolean

    fun deleteByIdUserIdAndIdGroupId(userId: Long, groupId: Long)

    fun countByIdUserId(userId: Long): Long
}
