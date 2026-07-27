package com.davismariotti.campalert.model

import com.davismariotti.campalert.provider.Provider
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.io.Serializable

@Embeddable
data class UserProviderQuotaOverrideId(
    @Column(name = "user_id")
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "provider")
    val provider: Provider,
) : Serializable

/** Per-user, per-provider active-search-request override; presence of a row always wins over group/global defaults. */
@Entity
@Table(name = "user_provider_quota_overrides")
data class UserProviderQuotaOverride(
    @EmbeddedId
    val id: UserProviderQuotaOverrideId,

    @Column(name = "max_active", nullable = false)
    val maxActive: Int,
)
