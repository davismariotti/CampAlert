package com.davismariotti.campalert.model

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable

@Embeddable
data class GroupAuthorityId(
    @Column(name = "group_id")
    val groupId: Long,

    @Column(name = "authority")
    val authority: String,
) : Serializable

@Entity
@Table(name = "group_authorities")
data class GroupAuthority(
    @EmbeddedId
    val id: GroupAuthorityId,
)
