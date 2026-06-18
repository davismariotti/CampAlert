package com.davismariotti.campalert.service.notification

import com.davismariotti.campalert.model.OutboxType
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.notification.CampsiteAlertNotification
import com.davismariotti.campalert.notification.PendingNotification
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CampsiteAlertNotificationTest {
    private val user = User(id = 1L, email = "user@example.com", passwordHash = "hash")

    private val request = SearchRequest(
        id = 1L,
        startDay = LocalDate.now().plusDays(10),
        nights = 2,
        groupSize = 2,
        campsiteId = 99,
        name = "Test Trip",
        campgroundName = "Upper Pines",
        completed = false,
    )

    @Test
    fun `getSmsContent includes campground name, date range, and URL for available alert`() {
        val notifications = listOf(PendingNotification(request = request, type = OutboxType.AVAILABLE, outboxId = 1L))
        val notification = CampsiteAlertNotification(user, available = notifications, gone = emptyList())

        val content = notification.getSmsContent()

        assertTrue(content.isPresent)
        val body = content.get()
        assertTrue(body.contains("Upper Pines"))
        assertTrue(body.contains("recreation.gov/camping/campgrounds/99"))
        assertTrue(body.contains("Reply PAUSE to snooze"))
        assertTrue(body.contains("Reply STOP to unsubscribe"))
    }

    @Test
    fun `getSmsContent includes count header for multiple available alerts`() {
        val req2 = request.copy(id = 2, campsiteId = 100, campgroundName = "Yosemite Valley")
        val notifications = listOf(
            PendingNotification(request = request, type = OutboxType.AVAILABLE, outboxId = 1L),
            PendingNotification(request = req2, type = OutboxType.AVAILABLE, outboxId = 2L),
        )
        val notification = CampsiteAlertNotification(user, available = notifications, gone = emptyList())

        val body = notification.getSmsContent().get()

        assertTrue(body.contains("Upper Pines"))
        assertTrue(body.contains("Yosemite Valley"))
        assertTrue(body.contains("2 campgrounds available"), "Expected count header for 2 notifications")
    }

    @Test
    fun `getSmsContent includes unavailability message for gone alerts`() {
        val notifications = listOf(PendingNotification(request = request, type = OutboxType.UNAVAILABLE, outboxId = 1L))
        val notification = CampsiteAlertNotification(user, available = emptyList(), gone = notifications)

        val body = notification.getSmsContent().get()

        assertTrue(body.contains("Upper Pines"))
        assertTrue(body.contains("no longer available"))
        assertTrue(body.contains("We'll alert you if it reopens"))
    }

    @Test
    fun `getSmsContent returns empty for empty available and gone lists`() {
        val notification = CampsiteAlertNotification(user, available = emptyList(), gone = emptyList())

        assertFalse(notification.getSmsContent().isPresent)
    }

    @Test
    fun `getSmsContent combines available and gone sections with double newline`() {
        val availableReq = request.copy(id = 1, campgroundName = "Upper Pines")
        val goneReq = request.copy(id = 2, campgroundName = "Lower Pines")
        val notification = CampsiteAlertNotification(
            user,
            available = listOf(PendingNotification(request = availableReq, type = OutboxType.AVAILABLE, outboxId = 1L)),
            gone = listOf(PendingNotification(request = goneReq, type = OutboxType.UNAVAILABLE, outboxId = 2L)),
        )

        val body = notification.getSmsContent().get()

        assertTrue(body.contains("Upper Pines"))
        assertTrue(body.contains("Lower Pines"))
        assertTrue(body.contains("\n\n"))
    }
}
