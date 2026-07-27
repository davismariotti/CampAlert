package com.davismariotti.campalert.model

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable

@Embeddable
data class GroupMemberId(
    @Column(name = "user_id")
    val userId: Long,

    @Column(name = "group_id")
    val groupId: Long,
) : Serializable

@Entity
@Table(name = "group_members")
data class GroupMember(
    @EmbeddedId
    val id: GroupMemberId,
)
