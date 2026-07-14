package com.davismariotti.campalert.model

import com.davismariotti.campalert.provider.Provider
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

    @Enumerated(EnumType.STRING)
    @Column(name = "provider")
    val provider: Provider,

    @Column(name = "target_id")
    val targetId: String,
) : Serializable
