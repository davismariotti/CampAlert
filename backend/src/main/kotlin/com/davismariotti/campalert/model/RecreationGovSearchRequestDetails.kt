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

/** Recreation.gov-only search request data (loop selection) — split out of `search_requests` so that table stays provider-agnostic. A row only exists when the user selected specific loops; no row means "watch all loops". */
@Entity
@Table(name = "recreation_gov_search_request_details")
class RecreationGovSearchRequestDetails {
    @Id
    @Column(name = "search_request_id")
    var searchRequestId: Long = 0

    @OneToOne
    @MapsId
    @JoinColumn(name = "search_request_id")
    var searchRequest: SearchRequest? = null

    @Column(name = "loops", columnDefinition = "json", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    var loops: List<String> = emptyList()
}
