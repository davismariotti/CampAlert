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

/**
 * ReserveCalifornia-only search request data — split out of `search_requests` so that table stays
 * provider-agnostic, mirroring [CampLifeSearchRequestDetails]. [unitCategoryId]/[sleepingUnitId]/
 * [minVehicleLength]/[amenityIds] are passed straight through to ReserveCalifornia's grid request
 * body and matched via its `IsFiltered` response flag — never resolved locally (see
 * `reserve-california-availability-provider` spec). Unlike CampLife's details row, [placeId] is
 * always populated regardless of whether any filter was set: it's needed unconditionally to build
 * this provider's booking link (`https://www.reservecalifornia.com/park/{placeId}/{facilityId}`),
 * which has no other source once a search request exists (design.md D11/D21). [unitTypeGroupIds] is
 * the loop-picker filter (ReserveCalifornia's `UnitTypesGroupIds`, D5's loop-equivalent grouping) —
 * added during implementation alongside the other filters once it became clear the generic `loops`
 * wire field needs a real target field distinct from [unitCategoryId] (a coarser category, not the
 * loop concept).
 */
@Entity
@Table(name = "reserve_california_search_request_details")
class ReserveCaliforniaSearchRequestDetails {
    @Id
    @Column(name = "search_request_id")
    var searchRequestId: Long = 0

    @OneToOne
    @MapsId
    @JoinColumn(name = "search_request_id")
    var searchRequest: SearchRequest? = null

    @Column(name = "unit_category_id")
    var unitCategoryId: Int? = null

    @Column(name = "unit_type_group_ids", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    var unitTypeGroupIds: List<Int>? = null

    @Column(name = "sleeping_unit_id")
    var sleepingUnitId: Int? = null

    @Column(name = "min_vehicle_length")
    var minVehicleLength: Int? = null

    @Column(name = "amenity_ids", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    var amenityIds: List<Int>? = null

    @Column(name = "place_id")
    var placeId: Int = 0
}
