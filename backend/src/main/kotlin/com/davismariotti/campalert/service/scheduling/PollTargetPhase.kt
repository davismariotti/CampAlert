package com.davismariotti.campalert.service.scheduling

import com.davismariotti.campalert.model.Provider
import com.davismariotti.campalert.model.TargetType
import kotlin.math.absoluteValue

/** Deterministic, evenly-distributed (in expectation) phase offset per poll target — see design decisions. */
object PollTargetPhase {
    fun phaseOffsetMs(
        targetType: TargetType,
        provider: Provider,
        targetId: String,
        intervalMs: Long
    ): Long {
        require(intervalMs > 0) { "intervalMs must be positive" }
        val hash = "$targetType:$provider:$targetId".hashCode().toLong().absoluteValue
        return hash % intervalMs
    }
}
