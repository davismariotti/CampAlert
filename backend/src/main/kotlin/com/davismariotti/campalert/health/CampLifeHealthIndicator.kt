package com.davismariotti.campalert.health

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component("campLife")
class CampLifeHealthIndicator(
    @Value("\${camplife.baseUrl}") baseUrl: String,
) : HttpHealthIndicator(baseUrl)
