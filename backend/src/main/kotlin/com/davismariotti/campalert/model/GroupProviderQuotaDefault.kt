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
data class GroupProviderQuotaDefaultId(
    @Column(name = "group_id")
    val groupId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "provider")
    val provider: Provider,
) : Serializable

/** Per-provider active-search-request default for a group; absence of a row means uncapped at this level. */
@Entity
@Table(name = "group_provider_quota_defaults")
data class GroupProviderQuotaDefault(
    @EmbeddedId
    val id: GroupProviderQuotaDefaultId,

    @Column(name = "max_active", nullable = false)
    val maxActive: Int,
)
