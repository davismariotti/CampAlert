package com.davismariotti.campalert.service.scheduling

import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@EnableScheduling
class Scheduler(
    val availabilityChecker: AvailabilityChecker
) {
    @Scheduled(fixedDelayString = $$"${campfinder.polling.interval-ms}")
    fun execute() {
        availabilityChecker.processSearchRequests()
    }
}
