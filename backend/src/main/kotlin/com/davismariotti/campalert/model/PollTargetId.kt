package com.davismariotti.campalert.model

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.io.Serializable

@Embeddable
data class PollTargetId(
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type")
    val targetType: TargetType,

    @Column(name = "target_id")
    val targetId: String,
) : Serializable
