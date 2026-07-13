package com.davismariotti.campalert.service.scheduling

import com.davismariotti.campalert.repository.PermitSearchRequestRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * Backfills/reconciles `poll_target_state` on every startup: ensures a row exists for every
 * currently-active campground and permit target. Idempotent (ensureTarget is ON CONFLICT DO
 * NOTHING), so this doubles as continuous self-healing rather than a one-off migration step.
 */
@Component
class PollTargetReconciliationRunner(
    private val searchRequestRepository: SearchRequestRepository,
    private val permitSearchRequestRepository: PermitSearchRequestRepository,
    private val pollTargetRegistrationService: PollTargetRegistrationService,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val campgroundTargets = searchRequestRepository.findDistinctActiveCampsiteTargets()
        campgroundTargets.forEach { pollTargetRegistrationService.ensureCampgroundTarget(it.campsiteId, it.provider) }

        val permitTargets = permitSearchRequestRepository.findDistinctActivePermitTargets()
        permitTargets.forEach { pollTargetRegistrationService.ensurePermitTarget(it.permitId, it.provider) }

        log.info("Poll target reconciliation complete campgrounds={} permits={}", campgroundTargets.size, permitTargets.size)
    }
}
