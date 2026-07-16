package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.model.CreateSearchRequestBody
import com.davismariotti.campalert.exception.NoVerifiedPhoneException
import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.provider.camplife.CampLifeCatalogCache
import com.davismariotti.campalert.repository.NotificationOutboxRepository
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.service.TimezoneResolutionService
import com.davismariotti.campalert.service.scheduling.PollTargetRegistrationService
import com.davismariotti.campalert.service.scheduling.ProviderSearchWindowProperties
import com.davismariotti.campalert.service.turnstile.TurnstileService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDate

class PhoneGateTest {
    private val searchRequestRepository = mock(SearchRequestRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val phoneNumberRepository = mock(PhoneNumberRepository::class.java)
    private val notificationOutboxRepository = mock(NotificationOutboxRepository::class.java)
    private val timezoneResolutionService = mock(TimezoneResolutionService::class.java)
    private val pollTargetRegistrationService = mock(PollTargetRegistrationService::class.java)
    private val campLifeCatalogCache = mock(CampLifeCatalogCache::class.java)
    private val providerSearchWindowProperties = mock(ProviderSearchWindowProperties::class.java)
    private val turnstileService = mock(TurnstileService::class.java)
    private val delegate =
        SearchRequestsDelegateImpl(
            searchRequestRepository,
            userRepository,
            phoneNumberRepository,
            notificationOutboxRepository,
            timezoneResolutionService,
            pollTargetRegistrationService,
            campLifeCatalogCache,
            providerSearchWindowProperties,
            turnstileService,
        )

    private val testUser = User(id = 1L, email = "test@example.com", passwordHash = "hash")

    private fun setCurrentUser() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("test@example.com", null, emptyList())
        `when`(userRepository.findByEmail("test@example.com")).thenReturn(testUser)
    }

    @Test
    fun `createSearchRequest returns 422 when user has no verified phone`() {
        setCurrentUser()
        `when`(phoneNumberRepository.countByUserIdAndStatus(1L, PhoneNumberStatus.VERIFIED)).thenReturn(0L)

        val body =
            CreateSearchRequestBody(
                startDay = LocalDate.now().plusDays(7),
                nights = 2,
                groupSize = 2,
                campsiteId = 123,
                campgroundName = "Test Campground",
                name = "Test",
                turnstileToken = "test-token",
            )
        val ex = assertThrows(NoVerifiedPhoneException::class.java) { delegate.createSearchRequest(body) }

        assertEquals(422, ex.httpStatus.value())
        assertEquals("NO_VERIFIED_PHONE", ex.code)
    }
}
