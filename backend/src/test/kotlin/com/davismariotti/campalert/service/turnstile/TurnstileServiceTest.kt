package com.davismariotti.campalert.service.turnstile

import com.davismariotti.campalert.config.TurnstileProperties
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import retrofit2.Call
import retrofit2.Response

class TurnstileServiceTest {
    private val turnstileApi = mock(TurnstileApi::class.java)
    private val service = TurnstileService(turnstileApi, TurnstileProperties(secretKey = "secret"))

    @Suppress("UNCHECKED_CAST")
    private fun mockCall(response: Response<SiteverifyResponse>): Call<SiteverifyResponse> {
        val call = mock(Call::class.java) as Call<SiteverifyResponse>
        `when`(call.execute()).thenReturn(response)
        return call
    }

    @Test
    fun `verified success returns true`() {
        val call = mockCall(Response.success(SiteverifyResponse(success = true)))
        `when`(turnstileApi.siteverify(anyString(), anyString())).thenReturn(call)

        assertTrue(service.verify("token"))
    }

    @Test
    fun `verified failure returns false`() {
        val call = mockCall(Response.success(SiteverifyResponse(success = false)))
        `when`(turnstileApi.siteverify(anyString(), anyString())).thenReturn(call)

        assertFalse(service.verify("token"))
    }

    @Test
    fun `network error fails open`() {
        val call = mock(Call::class.java)
        `when`(call.execute()).thenThrow(RuntimeException("timeout"))
        @Suppress("UNCHECKED_CAST")
        `when`(turnstileApi.siteverify(anyString(), anyString())).thenReturn(call as Call<SiteverifyResponse>)

        assertTrue(service.verify("token"))
    }

    @Test
    fun `non-2xx response fails open`() {
        val errorBody = "{}".toResponseBody("application/json".toMediaType())
        val call = mockCall(Response.error(502, errorBody))
        `when`(turnstileApi.siteverify(anyString(), anyString())).thenReturn(call)

        assertTrue(service.verify("token"))
    }
}
