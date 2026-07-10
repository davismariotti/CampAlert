package com.davismariotti.campalert.recreation

import com.fasterxml.jackson.annotation.JsonProperty

/** `GET search/suggest?q=...&geocoder=true` — mixed inventory typeahead; filter to `entityType == "permit"`. */
data class SearchSuggestResponse(
    @JsonProperty("inventory_suggestions") val inventorySuggestions: List<SearchSuggestion>? = null,
)

data class SearchSuggestion(
    @JsonProperty("entity_id") val entityId: String,
    @JsonProperty("entity_type") val entityType: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("parent_entity_id") val parentEntityId: String? = null,
    @JsonProperty("parent_entity_type") val parentEntityType: String? = null,
    @JsonProperty("parent_name") val parentName: String? = null,
    @JsonProperty("state_code") val stateCode: String? = null,
)
