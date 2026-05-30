package com.davismariotti.campfinder.recreation

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import com.fasterxml.jackson.annotation.JsonValue

enum class AvailabilityType(private val value: String) {
    AVAILABLE("Available"),
    NOT_RESERVABLE("Not Reservable"),
    NOT_YET_RELEASED("NYR"),
    OPEN("Open"),
    RESERVED("Reserved"),

    @JsonEnumDefaultValue
    UNKNOWN("Unknown");

    @JsonValue
    fun toValue(): String {
        return value
    }

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromValue(value: String): AvailabilityType {
            return values().find { it.value.equals(value, ignoreCase = true) } ?: UNKNOWN
        }
    }
}
