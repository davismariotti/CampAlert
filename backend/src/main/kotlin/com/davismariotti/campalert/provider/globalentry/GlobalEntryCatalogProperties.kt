package com.davismariotti.campalert.provider.globalentry

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "campfinder.globalentry.catalog")
data class GlobalEntryCatalogProperties(
    val ttlDays: Long = 7,
    val staleAfterDays: Long = 3,
)
