package com.davismariotti.campalert.provider.recreation

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
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
    @JsonProperty("code") val code: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("type") val type: PermitDivisionType? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("district") val district: String? = null,
    @JsonProperty("children") val children: List<String> = emptyList(),
    @JsonProperty("entry_ids") val entryIds: List<String> = emptyList(),
    @JsonProperty("exit_ids") val exitIds: List<String> = emptyList(),
)

/**
 * `division.type` values confirmed against live traffic. A Destination Zone division is the
 * structural signature [com.davismariotti.campalert.service.permit.PermitClassificationService] uses
 * to accept an unflagged permit as ZONE-type (Desolation); Camp Area is an ITINERARY-type leg
 * (Yellowstone); Entry Point is the TRAILHEAD-type signature (Yosemite, and a wide range of other
 * wilderness permits nationwide — the trailhead you enter from, not where you camp). UNKNOWN is the
 * fallback for anything else — permitcontent is an undocumented, unversioned endpoint, so an
 * unrecognized type must not be silently misread as one of these three.
 */
enum class PermitDivisionType {
    @JsonProperty("Destination Zone")
    DESTINATION_ZONE,

    @JsonProperty("Camp Area")
    CAMP_AREA,

    @JsonProperty("Entry Point")
    ENTRY_POINT,

    @JsonEnumDefaultValue
    UNKNOWN,
}

data class PermitRuleContent(
    @JsonProperty("division_id") val divisionId: String? = null,
    @JsonProperty("name") val name: PermitRuleName? = null,
    @JsonProperty("operation") val operation: String? = null,
    @JsonProperty("target") val target: String? = null,
    @JsonProperty("value") val value: Long? = null,
)

/**
 * `rule.name` values confirmed against live traffic. Only MaxGroupSize is read today
 * ([com.davismariotti.campalert.delegate.PermitsDelegateImpl.maxGroupSizeFor]); the other three are
 * modeled because they're already confirmed literal values, not guesses. UNKNOWN covers anything
 * else — the rule vocabulary isn't fully catalogued (other rule names, e.g.
 * MaxVehiclesPerReservation, are known to exist but haven't been observed in a live capture yet).
 */
enum class PermitRuleName {
    MaxGroupSize,
    StayLimitPerLeg,
    ConstantQuotaUsageDaily,
    QuotaUsageByMemberDaily,

    @JsonEnumDefaultValue
    UNKNOWN,
}
