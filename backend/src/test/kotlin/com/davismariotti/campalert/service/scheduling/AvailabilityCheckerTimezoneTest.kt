package com.davismariotti.campalert.service.scheduling

import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.availability.RecreationService
import com.davismariotti.campalert.service.state.AvailabilityStateService
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertTrue

class AvailabilityCheckerTimezoneTest {
    private val searchRequestRepository = mock(SearchRequestRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)

    private val checker = AvailabilityChecker(
        searchRequestRepository,
        userRepository,
        mock(RecreationService::class.java),
        mock(AvailabilityStateService::class.java),
        mock(ApplicationEventPublisher::class.java),
        threadPoolSize = 1,
    )

    private fun request(startDay: LocalDate, timezone: String?) =
        SearchRequest(
            id = 1,
            startDay = startDay,
            nights = 1,
            groupSize = 2,
            campsiteId = 233359,
            name = "test",
            completed = false,
            userId = 1L,
            campgroundTimezone = timezone,
        )

    @Test
    fun `request for today in campground timezone is not auto-completed`() {
        val today = LocalDate.now(ZoneId.of("America/Los_Angeles"))
        val req = request(today, "America/Los_Angeles")
        `when`(searchRequestRepository.findByCompletedFalse()).thenReturn(listOf(req))
        // Return no users so processing exits early — we only care the auto-complete block is skipped
        `when`(userRepository.findAllById(any())).thenReturn(emptyList<User>())

        checker.processSearchRequests()

        verify(searchRequestRepository, never()).save(org.mockito.Mockito.any(SearchRequest::class.java))
    }

    @Test
    fun `request for yesterday in campground timezone is auto-completed`() {
        val yesterday = LocalDate.now(ZoneId.of("America/Los_Angeles")).minusDays(1)
        val req = request(yesterday, "America/Los_Angeles")
        `when`(searchRequestRepository.findByCompletedFalse()).thenReturn(listOf(req))

        checker.processSearchRequests()

        val captor = ArgumentCaptor.forClass(SearchRequest::class.java)
        verify(searchRequestRepository).save(captor.capture())
        assertTrue(captor.value.completed)
    }

    @Test
    fun `null timezone falls back to UTC for completion check`() {
        val yesterdayUtc = LocalDate.now(ZoneOffset.UTC).minusDays(1)
        val req = request(yesterdayUtc, null)
        `when`(searchRequestRepository.findByCompletedFalse()).thenReturn(listOf(req))

        checker.processSearchRequests()

        val captor = ArgumentCaptor.forClass(SearchRequest::class.java)
        verify(searchRequestRepository).save(captor.capture())
        assertTrue(captor.value.completed)
    }

    // Suppress unchecked cast — Mockito's any() returns null for Kotlin non-nullable types but
    // works correctly here since we only need it as a matcher, not a real value.
    @Suppress("UNCHECKED_CAST")
    private fun <T> any(): T = org.mockito.Mockito.any<T>() as T
}
