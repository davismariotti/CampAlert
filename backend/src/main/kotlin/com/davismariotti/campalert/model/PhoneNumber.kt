package com.davismariotti.campalert.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class PhoneNumberStatus {
    PENDING_VERIFICATION,
    VERIFIED,
    OPTED_OUT
}

@Entity
@Table(name = "phone_numbers")
data class PhoneNumber(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "phone", nullable = false, unique = true)
    val phone: String,

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    val status: PhoneNumberStatus,

    @Column(name = "first_message_sent", nullable = false)
    val firstMessageSent: Boolean = false,

    @Column(name = "sms_consent_at", nullable = false)
    val smsConsentAt: Instant,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "verified_at")
    val verifiedAt: Instant? = null,
)
