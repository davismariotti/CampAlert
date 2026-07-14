package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.model.PermitSearchRequest
import com.davismariotti.campalert.provider.Provider
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

private class FakePermitAvailabilityProvider(
    override val provider: Provider
) : PermitAvailabilityProvider {
    override fun check(
        request: PermitSearchRequest,
        zoneCache: ZoneAvailabilityCache,
        itineraryCache: ItineraryAvailabilityCache,
    ): PermitAvailabilityResult = PermitAvailabilityResult(request, hasAvailability = false)
}

class PermitAvailabilityProviderRegistryTest {
    @Test
    fun `resolves the implementation registered for a provider`() {
        val recreationGov = FakePermitAvailabilityProvider(Provider.RECREATION_GOV)
        val registry = PermitAvailabilityProviderRegistry(listOf(recreationGov))

        assertSame(recreationGov, registry.forProvider(Provider.RECREATION_GOV))
    }

    @Test
    fun `throws a clear error when no implementation is registered for a provider`() {
        val registry = PermitAvailabilityProviderRegistry(emptyList())

        assertFailsWith<IllegalStateException> { registry.forProvider(Provider.RECREATION_GOV) }
    }
}
