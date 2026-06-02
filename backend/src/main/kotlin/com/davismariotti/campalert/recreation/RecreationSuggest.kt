package com.davismariotti.campalert.recreation

data class SuggestResponse(
    val inventorySuggestions: List<SuggestInventoryItem>?
)

data class SuggestInventoryItem(
    val entityId: String,
    val entityType: String,
    val name: String
)
