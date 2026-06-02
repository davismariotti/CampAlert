package com.davismariotti.campalert.service

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

    private val request =
        SearchRequest(
            id = 1,
            startDay = LocalDate.now().plusDays(10),
            nights = 2,
            groupSize = 2,
            campsiteId = 99,
            name = "Test Trip",
            completed = false,
        )

    private val campground =
        Campground(
            campsites =
                mapOf(
                    1 to
                        Campsite(
                            campsiteId = 1,
                            site = "001",
                            loop = "Loop A",
                            campsiteReserveType = "SITE_SPECIFIC",
                            minimumNumberOfPeople = 1,
                            maximumNumberOfPeople = 6,
                            availabilities = emptyMap(),
                            quantities = emptyMap(),
                        ),
                ),
        )

    @Test
    fun `buildMessage includes opt-out footer on first send`() {
        val body = invokePrivateBuildMessage(request, campground, firstMessageSent = false)
        assertTrue(body.contains("Reply STOP to unsubscribe"), "Footer missing on first send")
    }

    @Test
    fun `buildMessage omits opt-out footer on subsequent sends`() {
        val body = invokePrivateBuildMessage(request, campground, firstMessageSent = true)
        assertFalse(body.contains("Reply STOP to unsubscribe"), "Footer present on subsequent send")
    }

    @Test
    fun `buildMessage contains campground URL`() {
        val body = invokePrivateBuildMessage(request, campground, firstMessageSent = true)
        assertTrue(body.contains("recreation.gov/camping/campgrounds/99"))
    }

    @Test
    fun `buildMessage contains site info`() {
        val body = invokePrivateBuildMessage(request, campground, firstMessageSent = true)
        assertTrue(body.contains("Loop A") && body.contains("001"))
    }

    private fun invokePrivateBuildMessage(
        request: SearchRequest,
        campground: Campground,
        firstMessageSent: Boolean,
    ): String {
        val method =
            SmsNotificationService::class.java.getDeclaredMethod(
                "buildMessage",
                SearchRequest::class.java,
                Campground::class.java,
                Boolean::class.java,
            )
        method.isAccessible = true
        return method.invoke(service, request, campground, firstMessageSent) as String
    }
}
