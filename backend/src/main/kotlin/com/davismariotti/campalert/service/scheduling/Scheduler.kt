package com.davismariotti.campalert.service.scheduling

import com.davismariotti.campalert.service.permit.PermitAvailabilityChecker
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@EnableScheduling
class Scheduler(
    val availabilityChecker: AvailabilityChecker,
    val permitAvailabilityChecker: PermitAvailabilityChecker,
) {
    @Scheduled(fixedDelayString = $$"${campfinder.polling.interval-ms}")
    fun execute() {
        availabilityChecker.processSearchRequests()
        permitAvailabilityChecker.processSearchRequests()
    }
}
