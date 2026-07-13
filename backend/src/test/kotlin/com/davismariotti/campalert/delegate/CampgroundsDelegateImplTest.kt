package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.model.CampgroundSearchResult
import com.davismariotti.campalert.api.model.ProviderType
import com.davismariotti.campalert.camplife.CampLifeCatalogCache
import com.davismariotti.campalert.model.Provider
import com.davismariotti.campalert.recreation.RecreationApi
import com.davismariotti.campalert.recreation.RidbApi
import com.davismariotti.campalert.service.availability.CampgroundCatalogSearchProvider
import com.davismariotti.campalert.service.availability.CampgroundCatalogSearchProviderRegistry
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class FakeSearchProvider(
    override val provider: Provider,
    private val results: () -> List<CampgroundSearchResult>,
) : CampgroundCatalogSearchProvider {
    override fun search(query: String): List<CampgroundSearchResult> = results()
}

class CampgroundsDelegateImplTest {
    private val recreationApi = mock(RecreationApi::class.java)
    private val ridbApi = mock(RidbApi::class.java)
    private val circuitBreakerRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
    private val campLifeCatalogCache = mock(CampLifeCatalogCache::class.java)

    private fun delegate(vararg providers: CampgroundCatalogSearchProvider) =
        CampgroundsDelegateImpl(
            recreationApi,
            ridbApi,
            circuitBreakerRegistry,
            CampgroundCatalogSearchProviderRegistry(providers.toList()),
            campLifeCatalogCache,
        )

    @Test
    fun `unscoped search merges results from every registered provider`() {
        val recreationResults = listOf(CampgroundSearchResult(id = 1, name = "Elkmont", provider = Provider.RECREATION_GOV.toApi()))
        val campLifeResults = listOf(CampgroundSearchResult(id = 791, name = "Collins Lake", provider = Provider.CAMPLIFE.toApi()))
        val d = delegate(
            FakeSearchProvider(Provider.RECREATION_GOV) { recreationResults },
            FakeSearchProvider(Provider.CAMPLIFE) { campLifeResults },
        )

        val response = d.searchCampgrounds("lake", null)

        assertEquals(200, response.statusCode.value())
        assertEquals(setOf(1, 791), response.body?.map { it.id }?.toSet())
    }

    @Test
    fun `scoped search only returns the requested provider's results`() {
        val recreationResults = listOf(CampgroundSearchResult(id = 1, name = "Elkmont", provider = Provider.RECREATION_GOV.toApi()))
        val d = delegate(
            FakeSearchProvider(Provider.RECREATION_GOV) { recreationResults },
            FakeSearchProvider(Provider.CAMPLIFE) { error("should not be called") },
        )

        val response = d.searchCampgrounds("elkmont", ProviderType.RECREATION_GOV)

        assertEquals(listOf(1), response.body?.map { it.id })
    }

    @Test
    fun `one provider failing does not blank unscoped results from the other`() {
        val campLifeResults = listOf(CampgroundSearchResult(id = 791, name = "Collins Lake", provider = Provider.CAMPLIFE.toApi()))
        val d = delegate(
            FakeSearchProvider(Provider.RECREATION_GOV) { error("RIDB down") },
            FakeSearchProvider(Provider.CAMPLIFE) { campLifeResults },
        )

        val response = d.searchCampgrounds("lake", null)

        assertEquals(listOf(791), response.body?.map { it.id })
    }

    @Test
    fun `scoped search propagates a provider failure as an upstream error`() {
        val d = delegate(FakeSearchProvider(Provider.RECREATION_GOV) { error("RIDB down") })

        assertFailsWith<ResponseStatusException> { d.searchCampgrounds("elkmont", ProviderType.RECREATION_GOV) }
    }

    @Test
    fun `blank query is rejected`() {
        val d = delegate()

        assertFailsWith<ResponseStatusException> { d.searchCampgrounds("  ", null) }
    }
}
