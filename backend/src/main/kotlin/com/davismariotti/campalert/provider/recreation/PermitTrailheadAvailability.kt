package com.davismariotti.campalert.provider.recreation

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * `GET permitinyo/{id}/availabilityv2` — see [RecreationApi.getTrailheadPermitAvailability]. Unlike
 * [PermitZoneAvailabilityResponse], the payload is the date map directly: no `permit_id` or
 * `next_available_date` wrapper field was found on this endpoint.
 */
data class PermitTrailheadAvailabilityResponse(
    @com.fasterxml.jackson.annotation.JsonProperty("payload")
    val payload: Map<LocalDate, Map<String, PermitTrailheadAvailabilityCell>> = emptyMap(),
)

/**
 * One division-date cell. `isWalkup`/`notYetReleased`/`releaseDate` are the fixed fields confirmed
 * live (design.md: `notYetReleased` cells — quota not yet opened for online reservation — never count
 * as available regardless of any number present). Every other property on the cell is a quota-type
 * gate captured into [quotaGates] (name -> `{total, remaining}`) rather than hardcoded as named
 * properties: confirmed live that different permits expose different, sometimes simultaneous gates on
 * the same cell — Yosemite cells carry only `quota_usage_by_member_daily`; Enchantments cells carry
 * both that and `constant_quota_usage_daily` at once, disagreeing in value (`remaining: 8` per-person
 * alongside `remaining: 1` flat-per-permit). A fixed-field model would silently ignore whichever gate
 * it didn't happen to name, and risk exactly the false-positive class this open shape exists to
 * prevent. See [PermitTrailheadAvailabilityCellDeserializer] for how the split is done.
 */
@JsonDeserialize(using = PermitTrailheadAvailabilityCellDeserializer::class)
data class PermitTrailheadAvailabilityCell(
    val isWalkup: Boolean = false,
    val notYetReleased: Boolean = false,
    val releaseDate: ZonedDateTime? = null,
    val quotaGates: Map<String, PermitTrailheadQuotaGate> = emptyMap(),
)

data class PermitTrailheadQuotaGate(
    @com.fasterxml.jackson.annotation.JsonProperty("total") val total: Int = 0,
    @com.fasterxml.jackson.annotation.JsonProperty("remaining") val remaining: Int = 0,
)

/**
 * A cell's fixed, non-quota fields — everything else in the JSON object is treated as a quota-type
 * gate. Kept as a private constant here since it's the one place that needs to know the split.
 */
private val FIXED_CELL_FIELDS = setOf("is_walkup", "not_yet_released", "release_date")

/**
 * Splits a cell's fixed fields from its open set of quota-type gates. A hand-written deserializer
 * rather than `@JsonAnySetter` because Jackson's constructor-based Kotlin data class binding doesn't
 * support `@JsonAnySetter` cleanly, and this needs to be a normal immutable `data class` for the rest
 * of the codebase (equals/hashCode/copy) to work as expected.
 */
class PermitTrailheadAvailabilityCellDeserializer : StdDeserializer<PermitTrailheadAvailabilityCell>(PermitTrailheadAvailabilityCell::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): PermitTrailheadAvailabilityCell {
        val node: JsonNode = p.codec.readTree(p)
        val isWalkup = node.get("is_walkup")?.takeIf { !it.isNull }?.asBoolean() ?: false
        val notYetReleased = node.get("not_yet_released")?.takeIf { !it.isNull }?.asBoolean() ?: false
        val releaseDate = node
            .get("release_date")
            ?.takeIf { !it.isNull }
            ?.asText()
            ?.let { ZonedDateTime.parse(it) }
        val gates = node
            .fields()
            .asSequence()
            .filter { (key, _) -> key !in FIXED_CELL_FIELDS }
            .associate { (key, value) -> key to p.codec.treeToValue(value, PermitTrailheadQuotaGate::class.java) }
        return PermitTrailheadAvailabilityCell(isWalkup, notYetReleased, releaseDate, gates)
    }
}
