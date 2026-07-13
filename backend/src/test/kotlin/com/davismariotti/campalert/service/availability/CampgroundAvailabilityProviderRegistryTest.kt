package com.davismariotti.campalert.service.availability

import com.davismariotti.campalert.model.Provider
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.recreation.Campground
import org.junit.jupiter.api.Test
import java.time.YearMonth
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

private class FakeCampgroundAvailabilityProvider(
    override val provider: Provider
) : CampgroundAvailabilityProvider {
    override fun checkAvailability(
        searchRequest: SearchRequest,
        user: User,
        campgroundCache: ConcurrentHashMap<Pair<Int, YearMonth>, CompletableFuture<Campground>>?,
    ): AvailabilityResult = AvailabilityResult(searchRequest, hasAvailableSites = false, availableSiteCount = 0)
}

class CampgroundAvailabilityProviderRegistryTest {
    @Test
    fun `resolves the implementation registered for a provider`() {
        val recreationGov = FakeCampgroundAvailabilityProvider(Provider.RECREATION_GOV)
        val registry = CampgroundAvailabilityProviderRegistry(listOf(recreationGov))

        assertSame(recreationGov, registry.forProvider(Provider.RECREATION_GOV))
    }

    @Test
    fun `throws a clear error when no implementation is registered for a provider`() {
        val registry = CampgroundAvailabilityProviderRegistry(emptyList())

        assertFailsWith<IllegalStateException> { registry.forProvider(Provider.RECREATION_GOV) }
    }
}
