package com.davismariotti.campalert.health

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component("reserveCalifornia")
class ReserveCaliforniaHealthIndicator(
    @Value("\${reservecalifornia.baseUrl}") baseUrl: String,
) : HttpHealthIndicator(baseUrl)
