package com.davismariotti.campalert.service.scheduling

import com.davismariotti.campalert.service.permit.PermitAvailabilityChecker
import com.davismariotti.campalert.util.sleepJitter
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@EnableScheduling
class Scheduler(
    val availabilityChecker: AvailabilityChecker,
    val permitAvailabilityChecker: PermitAvailabilityChecker,
    @param:Value($$"${campfinder.polling.tick-jitter-ms}") private val tickJitterMs: Long,
) {
    @Scheduled(fixedDelayString = $$"${campfinder.polling.interval-ms}")
    fun execute() {
        sleepJitter(tickJitterMs)
        availabilityChecker.processSearchRequests()
        permitAvailabilityChecker.processSearchRequests()
    }
}
