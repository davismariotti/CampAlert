package com.davismariotti.campalert.model

import com.davismariotti.campalert.provider.Provider
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "global_provider_access")
data class GlobalProviderAccess(
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "provider")
    val provider: Provider,

    @Column(name = "enabled", nullable = false)
    val enabled: Boolean,
)
