package com.davismariotti.campalert.notification

import com.davismariotti.campalert.model.GlobalEntrySearchRequestState
import com.davismariotti.campalert.model.OutboxType
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.SearchRequestState
import com.davismariotti.campalert.provider.Provider
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class GlobalEntryAlertNotificationTest {
    private val link = "https://ttp.dhs.gov/schedulerui/schedule-interview/location?lang=en&vo=true&returnUrl=ttp-external&service=up"

    private fun request(
        id: Long = 1L,
        name: String = "SF Enrollment Center — San Francisco, CA",
        matchedDay: LocalDate? = LocalDate.of(2026, 10, 27),
        matchedTime: LocalTime? = LocalTime.of(11, 40),
        count: Int? = 3,
        timezone: String? = "America/Los_Angeles",
        startDay: LocalDate = LocalDate.of(2026, 10, 27),
        latestStartDay: LocalDate? = LocalDate.of(2026, 11, 3),
    ): SearchRequest {
        val req = SearchRequest(
            id = id,
            startDay = startDay,
            nights = 1,
            groupSize = 1,
            campsiteId = 5446,
            name = "n",
            campgroundName = name,
            campgroundTimezone = timezone,
            provider = Provider.GLOBAL_ENTRY,
            latestStartDay = latestStartDay,
        )
        val state = SearchRequestState()
        state.searchRequest = req
        state.matchedStartDay = matchedDay
        state.matchedEndDay = matchedDay
        req.state = state
        if (matchedTime != null || count != null) {
            val ge = GlobalEntrySearchRequestState()
            ge.searchRequest = req
            ge.matchedStartTime = matchedTime
            ge.matchedSlotCount = count
            req.globalEntryState = ge
        }
        return req
    }

    private fun pending(req: SearchRequest, type: OutboxType = OutboxType.AVAILABLE) = PendingNotification(req, type, outboxId = req.id!!)

    private fun sms(available: List<PendingNotification> = emptyList(), gone: List<PendingNotification> = emptyList()) = GlobalEntryAlertNotification(available, gone).sms()?.text

    @Test
    fun `available message names the center, count, earliest slot with timezone, link and footer`() {
        val text = sms(available = listOf(pending(request())))!!

        assertContains(text, "SF Enrollment Center — San Francisco, CA — 3 appointments available, earliest Tue Oct 27 at 11:40 AM PT")
        assertContains(text, link)
        assertContains(text, "Reply PAUSE to snooze. Reply STOP to unsubscribe.")
    }

    @Test
    fun `one slot is singular`() {
        assertContains(sms(available = listOf(pending(request(count = 1))))!!, "1 appointment available")
    }

    @Test
    fun `eastern centers are labelled ET`() {
        assertContains(sms(available = listOf(pending(request(timezone = "America/New_York"))))!!, "at 11:40 AM ET")
    }

    @Test
    fun `an unknown timezone omits the abbreviation`() {
        val text = sms(available = listOf(pending(request(timezone = null))))!!

        assertContains(text, "earliest Tue Oct 27 at 11:40 AM\n")
        assertFalse(text.contains("AM PT"))
    }

    @Test
    fun `reminders use the available wording`() {
        assertContains(sms(available = listOf(pending(request(), OutboxType.REMINDER)))!!, "appointments available")
    }

    @Test
    fun `missing match details fall back to the watched day range`() {
        val text = sms(available = listOf(pending(request(matchedTime = null, count = null))))!!

        assertContains(text, "appointments available Oct 27 – Nov 3")
    }

    @Test
    fun `a single watched day falls back to that day`() {
        val text = sms(available = listOf(pending(request(matchedTime = null, count = null, latestStartDay = null))))!!

        assertContains(text, "appointments available Oct 27")
        assertFalse(text.contains("–"))
    }

    @Test
    fun `several requests get a header and their own blocks`() {
        val text = sms(available = listOf(pending(request(id = 1L)), pending(request(id = 2L, name = "LAX Enrollment Center — Los Angeles, CA"))))!!

        assertContains(text, "2 Global Entry centers have appointments")
        assertContains(text, "LAX Enrollment Center — Los Angeles, CA — 3 appointments")
    }

    @Test
    fun `gone message uses appointment wording and no stay dates`() {
        val text = sms(gone = listOf(pending(request(), OutboxType.UNAVAILABLE)))!!

        assertContains(text, "SF Enrollment Center — San Francisco, CA — Global Entry appointments are no longer available for your dates.")
        assertContains(text, "We'll alert you if they reopen.")
        assertFalse(text.contains("night"))
    }

    @Test
    fun `available and gone parts are joined`() {
        val text = sms(available = listOf(pending(request(id = 1L))), gone = listOf(pending(request(id = 2L), OutboxType.UNAVAILABLE)))!!

        assertContains(text, "appointments available")
        assertContains(text, "no longer available")
    }

    @Test
    fun `nothing to say yields no sms`() {
        assertNull(GlobalEntryAlertNotification(emptyList(), emptyList()).sms())
    }

    @Test
    fun `timezone abbreviations are the generic short names`() {
        assertEquals("PT", GlobalEntryAlertNotification.timezoneAbbreviation("America/Los_Angeles"))
        assertEquals("CT", GlobalEntryAlertNotification.timezoneAbbreviation("America/Chicago"))
        assertNull(GlobalEntryAlertNotification.timezoneAbbreviation("Not/AZone"))
    }
}
