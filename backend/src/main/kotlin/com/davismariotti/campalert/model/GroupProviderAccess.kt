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
data class GroupProviderAccessId(
    @Column(name = "group_id")
    val groupId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "provider")
    val provider: Provider,
) : Serializable

/** Per-provider allow/deny default for a group; absence of a row means "no opinion," falls through. */
@Entity
@Table(name = "group_provider_access")
data class GroupProviderAccess(
    @EmbeddedId
    val id: GroupProviderAccessId,

    @Column(name = "enabled", nullable = false)
    val enabled: Boolean,
)
