package com.davismariotti.campalert.service.sms

import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.recreation.Campground
import com.davismariotti.campalert.recreation.Campsite
import com.davismariotti.campalert.repository.PhoneNumberRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.time.LocalDate

class SmsNotificationServiceTest {
    private val twilioConfig = mock(TwilioConfiguration::class.java)
    private val phoneNumberRepository = mock(PhoneNumberRepository::class.java)
    private val service = SmsNotificationService(twilioConfig, phoneNumberRepository)

    private val request = SearchRequest(
        id = 1,
        startDay = LocalDate.now().plusDays(10),
        nights = 2,
        groupSize = 2,
        campsiteId = 99,
        name = "Test Trip",
        campgroundName = "Upper Pines",
        completed = false,
    )

    private val campground = Campground(
        campsites = mapOf(
            1 to Campsite(
                campsiteId = 1, site = "001", loop = "Loop A",
                campsiteReserveType = "SITE_SPECIFIC",
                minimumNumberOfPeople = 1, maximumNumberOfPeople = 6,
                availabilities = emptyMap(), quantities = emptyMap(),
            ),
        ),
    )

    // --- legacy buildMessage (used by Pushover bypass path) ---

    @Test
    fun `buildMessage includes opt-out footer on first send`() {
        val body = invokeBuildLegacyMessage(request, campground, firstMessageSent = false)
        assertTrue(body.contains("Reply STOP to unsubscribe"), "Footer missing on first send")
    }

    @Test
    fun `buildMessage omits opt-out footer on subsequent sends`() {
        val body = invokeBuildLegacyMessage(request, campground, firstMessageSent = true)
        assertFalse(body.contains("Reply STOP to unsubscribe"), "Footer present on subsequent send")
    }

    @Test
    fun `buildMessage contains campground URL`() {
        val body = invokeBuildLegacyMessage(request, campground, firstMessageSent = true)
        assertTrue(body.contains("recreation.gov/camping/campgrounds/99"))
    }

    // --- aggregated message builder ---

    @Test
    fun `buildAggregatedMessage single campground includes name date range URL and PAUSE footer`() {
        val notifications = listOf(PendingNotification(request = request, type = "AVAILABLE", outboxId = 1L))
        val body = service.buildAggregatedMessage(notifications)
        assertTrue(body.contains("Upper Pines"))
        assertTrue(body.contains("recreation.gov/camping/campgrounds/99"))
        assertTrue(body.contains("Reply PAUSE to snooze"))
        assertTrue(body.contains("Reply STOP to unsubscribe"))
    }

    @Test
    fun `buildAggregatedMessage multiple campgrounds lists all`() {
        val req2 = request.copy(id = 2, campsiteId = 100, campgroundName = "Yosemite Valley")
        val notifications = listOf(
            PendingNotification(request = request, type = "AVAILABLE", outboxId = 1L),
            PendingNotification(request = req2, type = "AVAILABLE", outboxId = 2L),
        )
        val body = service.buildAggregatedMessage(notifications)
        assertTrue(body.contains("Upper Pines"))
        assertTrue(body.contains("Yosemite Valley"))
        assertTrue(body.contains("recreation.gov/camping/campgrounds/99"))
        assertTrue(body.contains("recreation.gov/camping/campgrounds/100"))
    }

    @Test
    fun `buildGoneMessage contains campground name and reopen note`() {
        val notifications = listOf(PendingNotification(request = request, type = "UNAVAILABLE", outboxId = 1L))
        val body = service.buildGoneMessage(notifications)
        assertTrue(body.contains("Upper Pines"))
        assertTrue(body.contains("reopen") || body.contains("resume"))
    }

    private fun invokeBuildLegacyMessage(
        request: SearchRequest,
        campground: Campground,
        firstMessageSent: Boolean,
    ): String {
        val method = SmsNotificationService::class.java.getDeclaredMethod(
            "buildLegacyMessage",
            SearchRequest::class.java,
            Campground::class.java,
            Boolean::class.java,
        )
        method.isAccessible = true
        return method.invoke(service, request, campground, firstMessageSent) as String
    }
}
