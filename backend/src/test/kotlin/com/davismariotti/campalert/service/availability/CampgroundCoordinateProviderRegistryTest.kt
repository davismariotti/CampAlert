package com.davismariotti.campalert.service.availability

import com.davismariotti.campalert.provider.Provider
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

private class FakeCampgroundCoordinateProvider(
    override val provider: Provider
) : CampgroundCoordinateProvider {
    override fun resolveCoordinates(campsiteId: Int): Pair<Double?, Double?> = null to null
}

class CampgroundCoordinateProviderRegistryTest {
    @Test
    fun `resolves the implementation registered for a provider`() {
        val recreationGov = FakeCampgroundCoordinateProvider(Provider.RECREATION_GOV)
        val registry = CampgroundCoordinateProviderRegistry(listOf(recreationGov))

        assertSame(recreationGov, registry.forProvider(Provider.RECREATION_GOV))
    }

    @Test
    fun `throws a clear error when no implementation is registered for a provider`() {
        val registry = CampgroundCoordinateProviderRegistry(emptyList())

        assertFailsWith<IllegalStateException> { registry.forProvider(Provider.RECREATION_GOV) }
    }
}
