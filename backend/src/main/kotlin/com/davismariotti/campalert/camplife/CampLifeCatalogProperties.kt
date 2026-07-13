package com.davismariotti.campalert.camplife

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "campfinder.camplife.catalog")
data class CampLifeCatalogProperties(
    val ttlDays: Long = 7,
    val staleAfterDays: Long = 3,
)
