package com.davismariotti.campalert.provider.reservecalifornia

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "campfinder.reservecalifornia.catalog")
data class ReserveCaliforniaCatalogProperties(
    val ttlDays: Long = 7,
)
