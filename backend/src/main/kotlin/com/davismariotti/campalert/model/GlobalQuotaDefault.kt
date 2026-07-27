package com.davismariotti.campalert.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** Singleton row (id always 1) holding the combined active-search-request default. */
@Entity
@Table(name = "global_quota_defaults")
data class GlobalQuotaDefault(
    @Id
    @Column(name = "id")
    val id: Short = 1,

    @Column(name = "max_active", nullable = false)
    val maxActive: Int,
) {
    companion object {
        const val SINGLETON_ID: Short = 1
    }
}
