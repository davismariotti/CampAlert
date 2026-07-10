package com.davismariotti.campalert.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "permit_itinerary_target")
class PermitItineraryTarget {
    @Id
    @Column(name = "permit_search_request_id")
    var permitSearchRequestId: Long = 0

    @OneToOne
    @MapsId
    @JoinColumn(name = "permit_search_request_id")
    var permitSearchRequest: PermitSearchRequest? = null

    @Column(name = "legs", columnDefinition = "json", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    var legs: List<PermitItineraryLeg> = emptyList()
}
