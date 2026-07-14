package com.davismariotti.campalert.health

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component("recreationGov")
class RecreationGovHealthIndicator(
    @Value($$"${recreation.baseUrl}") baseUrl: String,
) : HttpHealthIndicator(baseUrl)
