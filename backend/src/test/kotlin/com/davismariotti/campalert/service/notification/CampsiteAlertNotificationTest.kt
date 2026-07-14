package com.davismariotti.campalert.service.notification

import com.davismariotti.campalert.model.OutboxType
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.SearchRequestState
import com.davismariotti.campalert.notification.CampsiteAlertNotification
import com.davismariotti.campalert.notification.PendingNotification
import com.davismariotti.campalert.provider.Provider
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CampsiteAlertNotificationTest {
    private val request = SearchRequest(
        id = 1L,
        startDay = LocalDate.now().plusDays(10),
        nights = 2,
        groupSize = 2,
        campsiteId = 99,
        name = "Test Trip",
        campgroundName = "Upper Pines",
    ).also { req ->
        val st = SearchRequestState()
        st.searchRequest = req
        st.searchRequestId = 1L
        req.state = st
    }

    @Test
    fun `sms includes campground name, date range, and URL for available alert`() {
        val notifications = listOf(PendingNotification(request = request, type = OutboxType.AVAILABLE, outboxId = 1L))
        val notification = CampsiteAlertNotification(available = notifications, gone = emptyList())

        val content = notification.sms()

        assertTrue(content != null)
        val body = content!!.text
        assertTrue(body.contains("Upper Pines"))
        assertTrue(body.contains("recreation.gov/camping/campgrounds/99"))
        assertTrue(body.contains("Reply PAUSE to snooze"))
        assertTrue(body.contains("Reply STOP to unsubscribe"))
    }

    @Test
    fun `sms includes a camplife booking link for CampLife requests`() {
        val collinsLake = SearchRequest(
            id = 3L,
            startDay = LocalDate.now().plusDays(10),
            nights = 2,
            groupSize = 2,
            campsiteId = 791,
            name = "Collins Lake Trip",
            campgroundName = "Collins Lake",
            provider = Provider.CAMPLIFE,
        ).also { req ->
            val st = SearchRequestState()
            st.searchRequest = req
            st.searchRequestId = 3L
            req.state = st
        }
        val notifications = listOf(PendingNotification(request = collinsLake, type = OutboxType.AVAILABLE, outboxId = 3L))
        val notification = CampsiteAlertNotification(available = notifications, gone = emptyList())

        val body = notification.sms()!!.text

        assertTrue(body.contains("https://www.camplife.com/791/reservation/step1"))
        assertTrue(!body.contains("recreation.gov"))
    }

    @Test
    fun `sms includes count header for multiple available alerts`() {
        val req2 = request.copy(id = 2, campsiteId = 100, campgroundName = "Yosemite Valley")
        val notifications = listOf(
            PendingNotification(request = request, type = OutboxType.AVAILABLE, outboxId = 1L),
            PendingNotification(request = req2, type = OutboxType.AVAILABLE, outboxId = 2L),
        )
        val notification = CampsiteAlertNotification(available = notifications, gone = emptyList())

        val body = notification.sms()!!.text

        assertTrue(body.contains("Upper Pines"))
        assertTrue(body.contains("Yosemite Valley"))
        assertTrue(body.contains("2 campgrounds available"), "Expected count header for 2 notifications")
    }

    @Test
    fun `sms includes unavailability message for gone alerts`() {
        val notifications = listOf(PendingNotification(request = request, type = OutboxType.UNAVAILABLE, outboxId = 1L))
        val notification = CampsiteAlertNotification(available = emptyList(), gone = notifications)

        val body = notification.sms()!!.text

        assertTrue(body.contains("Upper Pines"))
        assertTrue(body.contains("no longer available"))
        assertTrue(body.contains("We'll alert you if it reopens"))
    }

    @Test
    fun `sms returns null for empty available and gone lists`() {
        val notification = CampsiteAlertNotification(available = emptyList(), gone = emptyList())

        assertNull(notification.sms())
    }

    @Test
    fun `sms combines available and gone sections with double newline`() {
        val availableReq = request.copy(id = 1, campgroundName = "Upper Pines")
        val goneReq = request.copy(id = 2, campgroundName = "Lower Pines")
        val notification = CampsiteAlertNotification(
            available = listOf(PendingNotification(request = availableReq, type = OutboxType.AVAILABLE, outboxId = 1L)),
            gone = listOf(PendingNotification(request = goneReq, type = OutboxType.UNAVAILABLE, outboxId = 2L)),
        )

        val body = notification.sms()!!.text

        assertTrue(body.contains("Upper Pines"))
        assertTrue(body.contains("Lower Pines"))
        assertTrue(body.contains("\n\n"))
    }
}
