package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.SearchRequestCheck
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CheckCountsProjection {
    fun getSearchRequestId(): Long

    fun getTotalChecks(): Long

    fun getAvailableChecks(): Long
}

interface AvgWindowProjection {
    fun getSearchRequestId(): Long

    fun getAvgWindowMinutes(): Double
}

interface SearchRequestCheckRepository : JpaRepository<SearchRequestCheck, Long> {
    fun countBySearchRequestId(searchRequestId: Long): Long

    @Query("SELECT COUNT(c) FROM SearchRequestCheck c WHERE c.searchRequestId = :id AND c.available = true")
    fun countAvailableBySearchRequestId(
        @Param("id") id: Long,
    ): Long

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
        @Param("id") id: Long,
    ): Double

    @Query(
        """
        SELECT c.searchRequestId AS searchRequestId,
               COUNT(c) AS totalChecks,
               SUM(CASE WHEN c.available = true THEN 1L ELSE 0L END) AS availableChecks
        FROM SearchRequestCheck c
        WHERE c.searchRequestId IN :ids
        GROUP BY c.searchRequestId
        """,
    )
    fun findCountStatsByRequestIds(
        @Param("ids") ids: List<Long>,
    ): List<CheckCountsProjection>

    @Query(
        nativeQuery = true,
        value = """
            WITH ordered AS (
                SELECT search_request_id,
                       checked_at,
                       available,
                       LAG(available) OVER (PARTITION BY search_request_id ORDER BY checked_at) AS prev_available,
                       LAG(checked_at) OVER (PARTITION BY search_request_id ORDER BY checked_at) AS prev_at
                FROM search_request_checks
                WHERE search_request_id IN (:ids)
            )
            SELECT search_request_id AS searchRequestId,
                   COALESCE(AVG(EXTRACT(EPOCH FROM (checked_at - prev_at)) / 60.0), 0.0) AS avgWindowMinutes
            FROM ordered
            WHERE available = false AND prev_available = true
            GROUP BY search_request_id
        """,
    )
    fun findAvgWindowByRequestIds(
        @Param("ids") ids: List<Long>,
    ): List<AvgWindowProjection>
}
