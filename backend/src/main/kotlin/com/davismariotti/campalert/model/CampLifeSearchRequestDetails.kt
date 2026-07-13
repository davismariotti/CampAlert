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
 * CampLife-only search request data — split out of `search_requests` so that table stays
 * provider-agnostic. [siteTypeId] is CampLife's grouping selection (the single siteType id a
 * Recreation.gov request would instead store as a list in `loops` — CampLife's own availability
 * endpoint only accepts one `siteTypeId` at a time, unlike Recreation.gov's loop model, so the
 * frontend's grouping picker is single-select for CampLife). [amenityIds] is the CampLife amenity
 * ID(s) to require. Both are passed straight through to the availability endpoint's
 * `siteTypeId`/`cgAmenity` fields and matched via its `isFiltered` response flag — never resolved
 * locally (CampLife exposes no reliable way to know a site's amenities without the availability call
 * itself; see the design conversation this shipped from). A row only exists when at least one of
 * these was actually set; no row means no CampLife-specific filtering beyond whatever
 * `site_ids`/date/group-size already apply.
 */
@Entity
@Table(name = "camplife_search_request_details")
class CampLifeSearchRequestDetails {
    @Id
    @Column(name = "search_request_id")
    var searchRequestId: Long = 0

    @OneToOne
    @MapsId
    @JoinColumn(name = "search_request_id")
    var searchRequest: SearchRequest? = null

    @Column(name = "site_type_id")
    var siteTypeId: Int? = null

    @Column(name = "amenity_ids", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    var amenityIds: List<Int>? = null
}
