package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.SearchRequestCheck
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SearchRequestCheckRepository : JpaRepository<SearchRequestCheck, Long> {
    fun countBySearchRequestId(searchRequestId: Int): Long

    @Query("SELECT COUNT(c) FROM SearchRequestCheck c WHERE c.searchRequestId = :id AND c.available = true")
    fun countAvailableBySearchRequestId(
        @Param("id") id: Int
    ): Long

    /**
     * Average availability window duration in minutes.
     * A window is from when a request became AVAILABLE to when it became UNAVAILABLE.
     * Uses LAG to find UNAVAILABLE rows whose previous row was AVAILABLE.
     */
    @Query(
        nativeQuery = true,
        value = """
            WITH ordered AS (
                SELECT checked_at, available,
                       LAG(available) OVER (ORDER BY checked_at) AS prev_available,
                       LAG(checked_at) OVER (ORDER BY checked_at) AS prev_at
                FROM search_request_checks
                WHERE search_request_id = :id
            )
            SELECT COALESCE(
                AVG(EXTRACT(EPOCH FROM (checked_at - prev_at)) / 60.0),
                0.0
            )
            FROM ordered
            WHERE available = false AND prev_available = true
        """,
    )
    fun computeAvgWindowMinutes(
        @Param("id") id: Int
    ): Double
}
