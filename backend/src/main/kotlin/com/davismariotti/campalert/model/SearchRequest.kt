package com.davismariotti.campalert.model

import com.davismariotti.campalert.provider.Provider
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "search_requests")
data class SearchRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    override val id: Long? = null,

    @Column(name = "start_day")
    val startDay: LocalDate,

    @Column(name = "nights")
    val nights: Int,

    @Column(name = "group_size")
    val groupSize: Int,

    @Column(name = "campsite_id")
    val campsiteId: Int,

    @Column(name = "site_ids", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    val siteIds: List<String>? = null,

    @Column(name = "name")
    val name: String,

    @Column(name = "user_id")
    override val userId: Long? = null,

    @Column(name = "campground_name")
    val campgroundName: String = "",

    @Column(name = "campground_timezone")
    val campgroundTimezone: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "provider")
    val provider: Provider = Provider.RECREATION_GOV,

    /** Latest acceptable arrival date for a flexible search; null means an exact-date search for `startDay` alone. */
    @Column(name = "latest_start_day")
    val latestStartDay: LocalDate? = null,
) : AlertableRequest {
    // Body properties: excluded from equals/hashCode/copy/toString to prevent circular reference.
    @OneToOne(mappedBy = "searchRequest", cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    lateinit var state: SearchRequestState

    @OneToOne(mappedBy = "searchRequest", cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    var recreationGovDetails: RecreationGovSearchRequestDetails? = null

    @OneToOne(mappedBy = "searchRequest", cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    var campLifeDetails: CampLifeSearchRequestDetails? = null

    @OneToOne(mappedBy = "searchRequest", cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    var reserveCaliforniaDetails: ReserveCaliforniaSearchRequestDetails? = null

    override var lastAvailabilityState: AvailabilityState?
        get() = state.lastAvailabilityState
        set(value) {
            state.lastAvailabilityState = value
        }

    override var lastNotifiedAt: Instant?
        get() = state.lastNotifiedAt
        set(value) {
            state.lastNotifiedAt = value
        }
}
