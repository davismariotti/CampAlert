package com.davismariotti.campalert.health

import com.davismariotti.campalert.service.sms.TwilioConfiguration
import com.twilio.rest.api.v2010.Account
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import org.springframework.boot.actuate.health.Status
import kotlin.test.assertEquals

class TwilioHealthIndicatorTest {
    private val config = mock(TwilioConfiguration::class.java).also {
        `when`(it.accountSid).thenReturn("ACtest")
    }
    private val indicator = TwilioHealthIndicator(config)

    @Test
    fun `UP when account fetch succeeds`() {
        val mockAccount = mock(Account::class.java)
        mockStatic(Account::class.java).use { staticAccount ->
            val fetcher = mock(com.twilio.rest.api.v2010.AccountFetcher::class.java)
            staticAccount.`when`<com.twilio.rest.api.v2010.AccountFetcher> {
                Account.fetcher("ACtest")
            }.thenReturn(fetcher)
            `when`(fetcher.fetch()).thenReturn(mockAccount)

            val health = indicator.health()
            assertEquals(Status.UP, health.status)
        }
    }

    @Test
    fun `DOWN when Twilio throws exception`() {
        val health = indicator.health()
        assertEquals(Status.DOWN, health.status)
        assertEquals(true, health.details.containsKey("reason"))
    }
}
