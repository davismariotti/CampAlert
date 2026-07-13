package com.davismariotti.campalert.model

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
import java.time.Instant

@Entity
@Table(name = "permit_search_requests")
data class PermitSearchRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    override val id: Long? = null,

    @Column(name = "permit_id")
    val permitId: String,

    @Column(name = "permit_name")
    val permitName: String,

    @Column(name = "permit_timezone")
    val permitTimezone: String? = null,

    @Column(name = "group_size")
    val groupSize: Int,

    @Column(name = "name")
    val name: String,

    @Column(name = "user_id")
    override val userId: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "search_type")
    val searchType: SearchType,

    @Enumerated(EnumType.STRING)
    @Column(name = "provider")
    val provider: Provider = Provider.RECREATION_GOV,
) : AlertableRequest {
    // Body properties: excluded from equals/hashCode/copy/toString to prevent circular reference.
    @OneToOne(mappedBy = "permitSearchRequest", cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    lateinit var state: PermitSearchRequestState

    @OneToOne(mappedBy = "permitSearchRequest", cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    var zoneTarget: PermitZoneTarget? = null

    @OneToOne(mappedBy = "permitSearchRequest", cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    var itineraryTarget: PermitItineraryTarget? = null

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
