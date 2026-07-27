package com.davismariotti.campalert.security

import com.davismariotti.campalert.model.GroupMember
import com.davismariotti.campalert.model.GroupMemberId
import com.davismariotti.campalert.repository.GroupMemberRepository
import com.davismariotti.campalert.repository.GroupRepository
import org.springframework.stereotype.Service

/**
 * Assigns a user to the baseline "Standard" group at registration time. [GroupSeeder] only
 * backfills users that already existed when the app last started — it never runs again, so a
 * brand-new registration needs this to get any permissions at all.
 */
@Service
class GroupMembershipService(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
) {
    fun assignToStandardGroup(userId: Long) {
        val standardGroupId = groupRepository.findByGroupName(GroupSeeder.STANDARD_GROUP_NAME)?.id
            ?: error("\"${GroupSeeder.STANDARD_GROUP_NAME}\" group not seeded — GroupSeeder must run before any user registers")
        if (!groupMemberRepository.existsByIdUserIdAndIdGroupId(userId, standardGroupId)) {
            groupMemberRepository.save(GroupMember(GroupMemberId(userId, standardGroupId)))
        }
    }
}
