package com.davismariotti.campalert.httpclient

import com.davismariotti.campalert.provider.camplife.CampLifeConfiguration
import com.davismariotti.campalert.provider.recreation.RecreationConfiguration
import org.junit.jupiter.api.Test
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * Guards design.md decision 10's isolation requirement: each provider's HTTP client must get its own
 * [InMemoryCookieJar] instance, never a shared singleton — otherwise cookies from one provider's
 * calls could leak into another's.
 */
class CookieJarIsolationTest {
    private val providerHttpClientFactory = ProviderHttpClientFactory(ProviderHttpClientProperties())

    @Test
    fun `RecreationConfiguration and CampLifeConfiguration build distinct cookie jar instances`() {
        val recreationConfiguration = RecreationConfiguration(baseUrl = "https://www.recreation.gov/api/", ridbBaseUrl = "https://ridb.recreation.gov/api/v1/", ridbApiKey = "test-key", providerHttpClientFactory = providerHttpClientFactory)
        val campLifeConfiguration = CampLifeConfiguration(baseUrl = "https://www.camplife.com/api/", providerHttpClientFactory = providerHttpClientFactory)

        val recreationCookieJar = recreationConfiguration.buildOkHttpClient().cookieJar
        val campLifeCookieJar = campLifeConfiguration.buildOkHttpClient().cookieJar

        assertTrue(recreationCookieJar is InMemoryCookieJar)
        assertTrue(campLifeCookieJar is InMemoryCookieJar)
        assertNotSame(recreationCookieJar, campLifeCookieJar)
    }

    @Test
    fun `each call to buildOkHttpClient produces a fresh cookie jar, never a cached singleton`() {
        val recreationConfiguration = RecreationConfiguration(baseUrl = "https://www.recreation.gov/api/", ridbBaseUrl = "https://ridb.recreation.gov/api/v1/", ridbApiKey = "test-key", providerHttpClientFactory = providerHttpClientFactory)

        val first = recreationConfiguration.buildOkHttpClient().cookieJar
        val second = recreationConfiguration.buildOkHttpClient().cookieJar

        assertNotSame(first, second)
    }
}
