package com.davismariotti.campalert.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "email", unique = true, nullable = false)
    val email: String,

    @Column(name = "password_hash", nullable = false)
    val passwordHash: String,

    @Column(name = "pushover_user_key")
    val pushoverUserKey: String? = null,

    @Column(name = "pushover_api_token")
    val pushoverApiToken: String? = null,

    @Column(name = "pushover_override_enabled", nullable = false)
    val pushoverOverrideEnabled: Boolean = false,

    @Column(name = "timezone", nullable = false)
    val timezone: String = "America/Los_Angeles",

    @Column(name = "email_verified_at")
    val emailVerifiedAt: Instant? = null,
)
