package com.davismariotti.campalert.provider.globalentry

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import java.io.IOException
import java.io.InputStream
import java.time.LocalDateTime

/**
 * Streams the scheduler's slots array and keeps only the start timestamp of bookable (`active > 0`)
 * slots. A multi-week response is ~600KB / thousands of slots, most of them inactive; deserializing it
 * into objects would cost several MB of transient garbage per center per poll cycle under a 256m heap,
 * so inactive slots are discarded as they are read and unknown fields are skipped.
 */
object GlobalEntrySlotParser {
    private val factory = JsonFactory()

    fun parseBookable(input: InputStream): List<LocalDateTime> {
        factory.createParser(input).use { parser ->
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw IOException("Expected a JSON array of slots but got ${parser.currentToken}")
            }
            val bookable = ArrayList<LocalDateTime>()
            while (parser.nextToken() == JsonToken.START_OBJECT) {
                var timestamp: String? = null
                var active = 0
                while (parser.nextToken() == JsonToken.FIELD_NAME) {
                    val field = parser.currentName()
                    parser.nextToken()
                    when (field) {
                        "timestamp" -> timestamp = parser.text
                        "active" -> active = parser.valueAsInt
                        else -> parser.skipChildren()
                    }
                }
                if (active > 0 && timestamp != null) bookable.add(LocalDateTime.parse(timestamp))
            }
            return bookable
        }
    }
}
