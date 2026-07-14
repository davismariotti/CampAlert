package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.model.Provider as ApiProvider
import com.davismariotti.campalert.api.model.ProviderType as ApiProviderType
import com.davismariotti.campalert.provider.Provider as DomainProvider

fun DomainProvider.toApiType(): ApiProviderType = ApiProviderType.valueOf(this.name)

fun DomainProvider.toApi(): ApiProvider = ApiProvider(type = this.toApiType(), name = this.friendlyName)

fun ApiProviderType.toModel(): DomainProvider = DomainProvider.valueOf(this.name)
