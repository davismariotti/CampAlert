package com.davismariotti.campalert.service

import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@EnableScheduling
class Scheduler(
    val requestProcessor: RequestProcessor
) {
    @Scheduled(fixedDelayString = "\${campfinder.polling.interval-ms}")
    fun execute() {
        requestProcessor.processSearchRequests()
    }
}
