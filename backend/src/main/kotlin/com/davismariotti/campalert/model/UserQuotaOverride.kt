package com.davismariotti.campalert.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** Per-user combined active-search-request override; presence of a row always wins over group/global defaults. */
@Entity
@Table(name = "user_quota_overrides")
data class UserQuotaOverride(
    @Id
    @Column(name = "user_id")
    val userId: Long,

    @Column(name = "max_active", nullable = false)
    val maxActive: Int,
)
