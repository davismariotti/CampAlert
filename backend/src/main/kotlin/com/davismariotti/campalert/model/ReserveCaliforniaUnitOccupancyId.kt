package com.davismariotti.campalert.model

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
data class ReserveCaliforniaUnitOccupancyId(
    @Column(name = "facility_id")
    val facilityId: Int,

    @Column(name = "unit_id")
    val unitId: Int,
) : Serializable
