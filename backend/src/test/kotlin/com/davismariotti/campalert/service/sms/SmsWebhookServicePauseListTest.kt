package com.davismariotti.campalert.service.sms

import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.SearchRequestState
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.davismariotti.campalert.repository.SearchRequestRepository
import com.davismariotti.campalert.service.PhoneNumberService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.LocalDate
import java.util.Optional
import kotlin.test.assertTrue

class SmsWebhookServicePauseListTest {
    private val searchRequestRepository = mock(SearchRequestRepository::class.java)
    private val smsConversationService = mock(SmsConversationService::class.java)
    private val service = SmsWebhookService(
        mock(PhoneNumberRepository::class.java),
        mock(PhoneNumberService::class.java),
        searchRequestRepository,
        smsConversationService,
    )

    private fun request(
        id: Long,
        name: String,
        provider: Provider,
        nights: Int,
        latestStartDay: LocalDate? = null
    ): SearchRequest {
        val req = SearchRequest(
            id = id,
            startDay = LocalDate.of(2026, 10, 27),
            nights = nights,
            groupSize = 1,
            campsiteId = 1,
            name = "n",
            campgroundName = name,
            provider = provider,
            latestStartDay = latestStartDay,
        )
        req.state = SearchRequestState().also { it.searchRequest = req }
        `when`(searchRequestRepository.findById(id)).thenReturn(Optional.of(req))
        return req
    }

    @Test
    fun `the pause list shows a stay's checkout for campsites and the watched day range for Global Entry`() {
        request(1L, "Pine Valley", Provider.RECREATION_GOV, nights = 2)
        request(2L, "SF Enrollment Center — San Francisco, CA", Provider.GLOBAL_ENTRY, nights = 1, latestStartDay = LocalDate.of(2026, 11, 20))
        `when`(smsConversationService.getContext("+12125550100")).thenReturn(listOf(1L, 2L))

        val reply = service.handlePause("+12125550100")

        assertTrue(reply.contains("1. Pine Valley 2026-10-27–2026-10-29"), reply)
        assertTrue(reply.contains("2. SF Enrollment Center — San Francisco, CA 2026-10-27–2026-11-20"), reply)
    }

    @Test
    fun `a single-day Global Entry request lists just that day`() {
        request(1L, "Pine Valley", Provider.RECREATION_GOV, nights = 2)
        request(2L, "SF Enrollment Center — San Francisco, CA", Provider.GLOBAL_ENTRY, nights = 1)
        `when`(smsConversationService.getContext("+12125550100")).thenReturn(listOf(1L, 2L))

        val reply = service.handlePause("+12125550100")

        assertTrue(reply.contains("2. SF Enrollment Center — San Francisco, CA 2026-10-27–2026-10-27"), reply)
    }
}
