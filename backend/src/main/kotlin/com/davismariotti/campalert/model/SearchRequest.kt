package com.davismariotti.campalert.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDate

@Entity
@Table(name = "search_requests_v2")
data class SearchRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int? = null,

    @Column(name = "start_day")
    val startDay: LocalDate,

    @Column(name = "nights")
    val nights: Int,

    @Column(name = "group_size")
    val groupSize: Int,

    @Column(name = "campsite_id")
    val campsiteId: Int,

    @Column(name = "loops", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    val loops: List<String>? = null,

    @Column(name = "name")
    val name: String,

    @Column(name = "completed")
    val completed: Boolean,

    @Column(name = "user_id")
    val userId: Long? = null,
)
