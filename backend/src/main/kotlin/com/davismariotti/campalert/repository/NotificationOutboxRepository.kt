package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.NotificationOutbox
import com.davismariotti.campalert.model.RequestType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface NotificationOutboxRepository : JpaRepository<NotificationOutbox, Long> {
    @Query(
        """
        SELECT n FROM NotificationOutbox n
        WHERE n.sentAt IS NULL
          AND n.missedAt IS NULL
          AND n.sendAfter <= :now
          AND (n.claimedAt IS NULL OR n.claimedAt < :staleThreshold)
        """,
    )
    fun findClaimable(
        @Param("now") now: Instant,
        @Param("staleThreshold") staleThreshold: Instant,
    ): List<NotificationOutbox>

    @Modifying
    @Query(
        """
        UPDATE NotificationOutbox n
        SET n.claimedAt = :claimedAt
        WHERE n.id IN :ids
          AND n.claimedAt IS NULL
          AND n.sentAt IS NULL
        """,
    )
    fun claimRows(
        @Param("ids") ids: List<Long>,
        @Param("claimedAt") claimedAt: Instant,
    ): Int

    fun findByRequestTypeAndRequestId(requestType: RequestType, requestId: Long): List<NotificationOutbox>

    @Query(
        "SELECT COUNT(n) FROM NotificationOutbox n WHERE n.requestType = :requestType AND n.requestId = :id AND n.type = 'AVAILABLE' AND n.missedAt IS NOT NULL",
    )
    fun countMissedWindowsByRequestTypeAndRequestId(
        @Param("requestType") requestType: RequestType,
        @Param("id") id: Long,
    ): Long

    @Query(
        """
        SELECT n.requestId AS requestId, COUNT(n) AS missedCount
        FROM NotificationOutbox n
        WHERE n.requestType = :requestType
          AND n.requestId IN :ids
          AND n.type = 'AVAILABLE'
          AND n.missedAt IS NOT NULL
        GROUP BY n.requestId
        """,
    )
    fun findMissedWindowCountsByRequestTypeAndRequestIds(
        @Param("requestType") requestType: RequestType,
        @Param("ids") ids: List<Long>,
    ): List<MissedWindowsProjection>

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM NotificationOutbox n WHERE n.requestType = :requestType AND n.requestId = :requestId")
    fun deleteByRequestTypeAndRequestId(
        @Param("requestType") requestType: RequestType,
        @Param("requestId") requestId: Long,
    ): Int
}
