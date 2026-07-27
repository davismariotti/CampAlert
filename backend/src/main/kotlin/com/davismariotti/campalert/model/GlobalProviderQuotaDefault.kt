package com.davismariotti.campalert.model

import com.davismariotti.campalert.provider.Provider
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table

/** Per-provider global default cap; absence of a row means uncapped at this level. */
@Entity
@Table(name = "global_provider_quota_defaults")
data class GlobalProviderQuotaDefault(
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "provider")
    val provider: Provider,

    @Column(name = "max_active", nullable = false)
    val maxActive: Int,
)
