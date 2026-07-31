package com.davismariotti.campalert.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** Singleton row (id always 1) holding platform-wide toggles. */
@Entity
@Table(name = "platform_settings")
data class PlatformSettings(
    @Id
    @Column(name = "id")
    val id: Short = 1,

    @Column(name = "invite_only_enabled", nullable = false)
    val inviteOnlyEnabled: Boolean = false,
) {
    companion object {
        const val SINGLETON_ID: Short = 1
    }
}
