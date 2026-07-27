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
data class UserProviderAccessId(
    @Column(name = "user_id")
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "provider")
    val provider: Provider,
) : Serializable

/** Per-user, per-provider allow/deny override; presence of a row always wins over group/global defaults. */
@Entity
@Table(name = "user_provider_access")
data class UserProviderAccess(
    @EmbeddedId
    val id: UserProviderAccessId,

    @Column(name = "enabled", nullable = false)
    val enabled: Boolean,
)
