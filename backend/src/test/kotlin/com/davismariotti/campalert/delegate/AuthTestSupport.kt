package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.service.UserResourceLimitsService
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any

/**
 * A [UserResourceLimitsService] mock for tests that build an `AuthDelegateImpl`: every auth response now
 * lists `enabledProviders`, so `isProviderAllowed` must return a value (a bare mock returns null and would
 * NPE). Access defaults to granted for every provider; tests that care re-stub it.
 */
fun userResourceLimitsServiceAllowingAllProviders(): UserResourceLimitsService =
    mock(UserResourceLimitsService::class.java).also {
        `when`(it.isProviderAllowed(anyLong(), any<Provider>()))
            .thenReturn(UserResourceLimitsService.EffectiveValue(true, UserResourceLimitsService.Source.GLOBAL_DEFAULT))
    }
