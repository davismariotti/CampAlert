package com.davismariotti.campalert.service

import com.davismariotti.campalert.repository.SearchRequestRepository
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class RequestProcessor(
    val searchRequestRepository: SearchRequestRepository,
    val recreationService: RecreationService
) {
    fun processSearchRequests() {
        val searchRequests = searchRequestRepository.findByCompletedFalse()

        searchRequests.forEach {
            if (it.startDay < LocalDate.now()) {
                searchRequestRepository.save(
                    it.copy(
                        completed = true
                    )
                )
                return@forEach
            }
            recreationService.checkAvailability(it)
        }
    }
}
