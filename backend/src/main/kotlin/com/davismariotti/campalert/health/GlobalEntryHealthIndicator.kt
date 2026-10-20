package com.davismariotti.campalert.health

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component("globalEntry")
class GlobalEntryHealthIndicator(
    @Value("\${globalentry.baseUrl}") baseUrl: String,
) : HttpHealthIndicator(baseUrl)
