package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.ReserveCaliforniaUnitOccupancy
import com.davismariotti.campalert.model.ReserveCaliforniaUnitOccupancyId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface ReserveCaliforniaUnitOccupancyRepository : JpaRepository<ReserveCaliforniaUnitOccupancy, ReserveCaliforniaUnitOccupancyId> {
    fun findByIdFacilityId(facilityId: Int): List<ReserveCaliforniaUnitOccupancy>

    fun findByIdFacilityIdAndIdUnitIdIn(facilityId: Int, unitIds: Collection<Int>): List<ReserveCaliforniaUnitOccupancy>

    /** PENDING rows, or FAILED rows whose retry cooldown (D15) has elapsed — the fan-out's own work queue for one facility. */
    @Query(
        """
        SELECT o FROM ReserveCaliforniaUnitOccupancy o
        WHERE o.id.facilityId = :facilityId
          AND (o.status = com.davismariotti.campalert.model.ReserveCaliforniaOccupancyStatus.PENDING
               OR (o.status = com.davismariotti.campalert.model.ReserveCaliforniaOccupancyStatus.FAILED AND o.nextAttemptAt <= :now))
        ORDER BY o.id.unitId
        """,
    )
    fun findFetchable(
        @Param("facilityId") facilityId: Int,
        @Param("now") now: Instant,
    ): List<ReserveCaliforniaUnitOccupancy>

    /** Every facility with outstanding work — read by the backstop resumption job (D16). */
    @Query(
        """
        SELECT DISTINCT o.id.facilityId FROM ReserveCaliforniaUnitOccupancy o
        WHERE o.status = com.davismariotti.campalert.model.ReserveCaliforniaOccupancyStatus.PENDING
           OR (o.status = com.davismariotti.campalert.model.ReserveCaliforniaOccupancyStatus.FAILED AND o.nextAttemptAt <= :now)
        """,
    )
    fun findFacilityIdsNeedingWarmup(
        @Param("now") now: Instant
    ): List<Int>

    @Query("SELECT DISTINCT o.id.facilityId FROM ReserveCaliforniaUnitOccupancy o")
    fun findKnownFacilityIds(): List<Int>

    /** D20: resets stale FETCHED/EXCLUDED rows (jittered per-unit freshness window elapsed) back to PENDING. */
    @Modifying
    @Query(
        """
        UPDATE ReserveCaliforniaUnitOccupancy o
        SET o.status = com.davismariotti.campalert.model.ReserveCaliforniaOccupancyStatus.PENDING,
            o.attempts = 0, o.nextAttemptAt = null, o.nextReconcileAt = null,
            o.maxOccupancy = null, o.minOccupancy = null, o.fetchedAt = null
        WHERE o.nextReconcileAt IS NOT NULL AND o.nextReconcileAt <= :now
        """,
    )
    fun resetStaleRows(
        @Param("now") now: Instant
    ): Int

    @Modifying
    @Query("DELETE FROM ReserveCaliforniaUnitOccupancy o WHERE o.id.facilityId = :facilityId AND o.id.unitId IN :unitIds")
    fun deleteByFacilityIdAndUnitIds(
        @Param("facilityId") facilityId: Int,
        @Param("unitIds") unitIds: Collection<Int>,
    ): Int
}
