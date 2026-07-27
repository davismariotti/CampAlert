package com.davismariotti.campalert.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** Combined (all-provider) active-search-request default for a group; one row per group that defines one. */
@Entity
@Table(name = "group_quota_defaults")
data class GroupQuotaDefault(
    @Id
    @Column(name = "group_id")
    val groupId: Long,

    @Column(name = "max_active", nullable = false)
    val maxActive: Int,
)
