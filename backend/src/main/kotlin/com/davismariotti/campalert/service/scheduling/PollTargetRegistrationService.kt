package com.davismariotti.campalert.service.scheduling

import com.davismariotti.campalert.model.TargetType
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.repository.PollTargetStateDao
import org.springframework.stereotype.Service

/** Ensures a `poll_target_state` row exists for a campground/permit as soon as it has an active request — idempotent, safe to call on every create/update. */
@Service
class PollTargetRegistrationService(
    private val pollTargetStateDao: PollTargetStateDao,
    private val providerPollingProperties: ProviderPollingProperties,
) {
    fun ensureCampgroundTarget(campsiteId: Int, provider: Provider) {
        pollTargetStateDao.ensureTarget(TargetType.CAMPGROUND, provider, campsiteId.toString(), providerPollingProperties.intervalFor(provider))
    }

    fun ensurePermitTarget(permitId: String, provider: Provider) {
        pollTargetStateDao.ensureTarget(TargetType.PERMIT, provider, permitId, providerPollingProperties.intervalFor(provider))
    }
}
