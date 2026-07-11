package com.davismariotti.campalert.util

private val chunkPattern = Regex("\\d+|\\D+")

/**
 * Splits strings into runs of digits and non-digits and compares digit runs numerically, so labels
 * like Recreation.gov permit zone names ("2 McConnell", "11 Rubicon") sort by their leading number
 * instead of lexicographically (which would put "11" before "2").
 */
val naturalOrder: Comparator<String> = Comparator { a, b ->
    val chunksA = chunkPattern.findAll(a).iterator()
    val chunksB = chunkPattern.findAll(b).iterator()

    while (chunksA.hasNext() && chunksB.hasNext()) {
        val chunkA = chunksA.next().value
        val chunkB = chunksB.next().value
        val result = if (chunkA[0].isDigit() && chunkB[0].isDigit()) {
            chunkA.toLong().compareTo(chunkB.toLong())
        } else {
            chunkA.compareTo(chunkB)
        }
        if (result != 0) return@Comparator result
    }
    chunksA.hasNext().compareTo(chunksB.hasNext())
}
