package com.davismariotti.campalert.provider.globalentry

import com.davismariotti.campalert.exception.BadRequestException
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** A Global Entry request's optional time-of-day window, center-local: [earliest] inclusive, [latest] exclusive, either null for open-ended. */
data class GlobalEntryTimeWindow(
    val earliest: LocalTime?,
    val latest: LocalTime?,
) {
    val isEmpty: Boolean get() = earliest == null && latest == null

    companion object {
        private val HH_MM = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
        private val FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

        /** Parses the wire `HH:mm` strings; throws [BadRequestException] if either is malformed or, when both are set, earliest is not strictly before latest. */
        fun parse(earliest: String?, latest: String?): GlobalEntryTimeWindow {
            val window = GlobalEntryTimeWindow(parseTime("earliestTime", earliest), parseTime("latestTime", latest))
            if (window.earliest != null && window.latest != null && !window.earliest.isBefore(window.latest)) {
                throw BadRequestException("earliestTime must be before latestTime")
            }
            return window
        }

        fun format(time: LocalTime?): String? = time?.format(FORMATTER)

        private fun parseTime(field: String, value: String?): LocalTime? {
            if (value == null) return null
            if (!HH_MM.matches(value)) throw BadRequestException("$field must be a time of day in HH:mm format")
            return LocalTime.parse(value)
        }
    }
}
