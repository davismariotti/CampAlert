package com.davismariotti.campalert.provider.recreation

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import com.fasterxml.jackson.annotation.JsonProperty

/** `GET search/suggest?q=...&geocoder=true` — mixed inventory typeahead; filter to `entityType == SearchEntityType.Permit`. */
data class SearchSuggestResponse(
    @JsonProperty("inventory_suggestions") val inventorySuggestions: List<SearchSuggestion>? = null,
)

data class SearchSuggestion(
    @JsonProperty("entity_id") val entityId: String,
    @JsonProperty("entity_type") val entityType: SearchEntityType,
    @JsonProperty("name") val name: String,
    @JsonProperty("parent_entity_id") val parentEntityId: String? = null,
    @JsonProperty("parent_entity_type") val parentEntityType: SearchEntityType? = null,
    @JsonProperty("parent_name") val parentName: String? = null,
    @JsonProperty("state_code") val stateCode: String? = null,
)

/**
 * `entity_type`/`parent_entity_type` values confirmed against live traffic (permit, recarea). The
 * endpoint mixes in other inventory kinds too (campgrounds, activity passes, per its own docstring)
 * whose exact literal casing hasn't been confirmed in a live capture, so anything else falls back to
 * UNKNOWN rather than guessing at a spelling.
 */
enum class SearchEntityType {
    @JsonProperty("permit")
    Permit,

    @JsonProperty("recarea")
    Recarea,

    @JsonEnumDefaultValue
    UNKNOWN,
}
