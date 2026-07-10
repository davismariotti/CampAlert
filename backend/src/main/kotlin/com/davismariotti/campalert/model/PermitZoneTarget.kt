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
import java.time.LocalDate

@Entity
@Table(name = "permit_zone_target")
class PermitZoneTarget {
    @Id
    @Column(name = "permit_search_request_id")
    var permitSearchRequestId: Long = 0

    @OneToOne
    @MapsId
    @JoinColumn(name = "permit_search_request_id")
    var permitSearchRequest: PermitSearchRequest? = null

    @Column(name = "division_ids", columnDefinition = "json", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    var divisionIds: List<String> = emptyList()

    @Column(name = "start_day", nullable = false)
    var startDay: LocalDate = LocalDate.now()

    @Column(name = "end_day", nullable = false)
    var endDay: LocalDate = LocalDate.now()
}
