package com.davismariotti.campalert.recreation

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * `GET permitcontent/{id}` — confirmed live against both a zone permit (Desolation, 233261) and an
 * itinerary permit (Yellowstone, 4675323); works for either type, so classification, permit detail,
 * and leg validation all share this one call/DTO (see [com.davismariotti.campalert.service.permit.PermitContentCache]).
 */
data class PermitContentResponse(
    @JsonProperty("payload") val payload: PermitContentPayload,
)

data class PermitContentPayload(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("recarea_name") val recareaName: String? = null,
    @JsonProperty("divisions") val divisions: Map<String, PermitDivisionContent> = emptyMap(),
    @JsonProperty("rules") val rules: List<PermitRuleContent> = emptyList(),
)

data class PermitDivisionContent(
    @JsonProperty("id") val id: String,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("district") val district: String? = null,
    @JsonProperty("children") val children: List<String> = emptyList(),
    @JsonProperty("entry_ids") val entryIds: List<String> = emptyList(),
    @JsonProperty("exit_ids") val exitIds: List<String> = emptyList(),
)

data class PermitRuleContent(
    @JsonProperty("division_id") val divisionId: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("operation") val operation: String? = null,
    @JsonProperty("target") val target: String? = null,
    @JsonProperty("value") val value: Long? = null,
)
