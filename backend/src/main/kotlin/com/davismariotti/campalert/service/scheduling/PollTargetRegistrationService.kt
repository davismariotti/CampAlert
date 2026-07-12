package com.davismariotti.campalert.service.scheduling

import com.davismariotti.campalert.model.TargetType
import com.davismariotti.campalert.repository.PollTargetStateDao
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/** Ensures a `poll_target_state` row exists for a campground/permit as soon as it has an active request — idempotent, safe to call on every create/update. */
@Service
class PollTargetRegistrationService(
    private val pollTargetStateDao: PollTargetStateDao,
    @param:Value($$"${campfinder.polling.interval-ms}") private val intervalMs: Long,
) {
    fun ensureCampgroundTarget(campsiteId: Int) {
        pollTargetStateDao.ensureTarget(TargetType.CAMPGROUND, campsiteId.toString(), intervalMs)
    }

    fun ensurePermitTarget(permitId: String) {
        pollTargetStateDao.ensureTarget(TargetType.PERMIT, permitId, intervalMs)
    }
}
