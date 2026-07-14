package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.model.CampgroundResponse
import com.davismariotti.campalert.api.model.CampgroundSearchResult
import com.davismariotti.campalert.api.model.LoopInfo
import com.davismariotti.campalert.api.model.ProviderType
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.service.availability.CampgroundCatalogProvider
import com.davismariotti.campalert.service.availability.CampgroundCatalogProviderRegistry
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class FakeCatalogProvider(
    override val provider: Provider,
    private val results: () -> List<CampgroundSearchResult> = { emptyList() },
    private val campground: () -> CampgroundResponse? = { null },
    private val loops: () -> List<LoopInfo> = { emptyList() },
) : CampgroundCatalogProvider {
    override fun search(query: String): List<CampgroundSearchResult> = results()

    override fun getCampground(id: Int): CampgroundResponse? = campground()

    override fun getLoops(id: Int): List<LoopInfo> = loops()
}

class CampgroundsDelegateImplTest {
    private fun delegate(vararg providers: CampgroundCatalogProvider) = CampgroundsDelegateImpl(CampgroundCatalogProviderRegistry(providers.toList()))

    @Test
    fun `unscoped search merges results from every registered provider`() {
        val recreationResults = listOf(CampgroundSearchResult(id = 1, name = "Elkmont", provider = Provider.RECREATION_GOV.toApi()))
        val campLifeResults = listOf(CampgroundSearchResult(id = 791, name = "Collins Lake", provider = Provider.CAMPLIFE.toApi()))
        val d = delegate(
            FakeCatalogProvider(Provider.RECREATION_GOV, results = { recreationResults }),
            FakeCatalogProvider(Provider.CAMPLIFE, results = { campLifeResults }),
        )

        val response = d.searchCampgrounds("lake", null)

        assertEquals(200, response.statusCode.value())
        assertEquals(setOf(1, 791), response.body?.map { it.id }?.toSet())
    }

    @Test
    fun `scoped search only returns the requested provider's results`() {
        val recreationResults = listOf(CampgroundSearchResult(id = 1, name = "Elkmont", provider = Provider.RECREATION_GOV.toApi()))
        val d = delegate(
            FakeCatalogProvider(Provider.RECREATION_GOV, results = { recreationResults }),
            FakeCatalogProvider(Provider.CAMPLIFE, results = { error("should not be called") }),
        )

        val response = d.searchCampgrounds("elkmont", ProviderType.RECREATION_GOV)

        assertEquals(listOf(1), response.body?.map { it.id })
    }

    @Test
    fun `one provider failing does not blank unscoped results from the other`() {
        val campLifeResults = listOf(CampgroundSearchResult(id = 791, name = "Collins Lake", provider = Provider.CAMPLIFE.toApi()))
        val d = delegate(
            FakeCatalogProvider(Provider.RECREATION_GOV, results = { error("RIDB down") }),
            FakeCatalogProvider(Provider.CAMPLIFE, results = { campLifeResults }),
        )

        val response = d.searchCampgrounds("lake", null)

        assertEquals(listOf(791), response.body?.map { it.id })
    }

    @Test
    fun `scoped search propagates a provider failure as an upstream error`() {
        val d = delegate(FakeCatalogProvider(Provider.RECREATION_GOV, results = { error("RIDB down") }))

        assertFailsWith<ResponseStatusException> { d.searchCampgrounds("elkmont", ProviderType.RECREATION_GOV) }
    }

    @Test
    fun `blank query is rejected`() {
        val d = delegate()

        assertFailsWith<ResponseStatusException> { d.searchCampgrounds("  ", null) }
    }

    @Test
    fun `getCampground dispatches to the requested provider and 404s on a missing result`() {
        val d = delegate(
            FakeCatalogProvider(Provider.RECREATION_GOV, campground = { CampgroundResponse(campsites = emptyMap()) }),
            FakeCatalogProvider(Provider.CAMPLIFE, campground = { null }),
        )

        assertEquals(200, d.getCampground(1, ProviderType.RECREATION_GOV).statusCode.value())
        assertEquals(404, d.getCampground(1, ProviderType.CAMPLIFE).statusCode.value())
    }

    @Test
    fun `getCampgroundLoops propagates a provider failure as an upstream error`() {
        val d = delegate(FakeCatalogProvider(Provider.RECREATION_GOV, loops = { error("RIDB down") }))

        assertFailsWith<ResponseStatusException> { d.getCampgroundLoops(1, ProviderType.RECREATION_GOV) }
    }
}
