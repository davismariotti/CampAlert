package com.davismariotti.campalert.service

import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class RequestProcessor(
    val searchRequestRepository: SearchRequestRepository,
    val userRepository: UserRepository,
    val recreationService: RecreationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun processSearchRequests() {
        searchRequestRepository.findByCompletedFalse().forEach { request ->
            if (request.startDay < LocalDate.now()) {
                searchRequestRepository.save(request.copy(completed = true))
                return@forEach
            }
            if (request.pauseReason != null) return@forEach
            val user = userRepository.findById(request.userId!!).orElse(null)
            if (user == null) {
                log.warn("No user found for search request=${request.id}, skipping")
                return@forEach
            }
            recreationService.checkAvailability(request, user)
        }
    }
}
