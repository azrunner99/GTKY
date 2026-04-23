package com.gtky.app.util

/**
 * Normalizes a display name to strict title case:
 * - Collapses runs of whitespace to single spaces, trims ends.
 * - For each whitespace-delimited word, uppercases the first letter and lowercases the rest.
 * - Hyphen-joined segments inside a word are each title-cased ("mary-jane" -> "Mary-Jane").
 * - Apostrophes do not trigger new segments ("o'brien" -> "O'brien", "mcdonald" -> "Mcdonald").
 *   This is an intentional trade-off: minimal rules, predictable behavior. Users can self-correct
 *   via the rename dialog if they need a specific mixed case.
 *
 * Examples:
 *   "alex smith"      -> "Alex Smith"
 *   "ALEX SMITH"      -> "Alex Smith"
 *   "alex S"          -> "Alex S"
 *   "  alex   s  "    -> "Alex S"
 *   "mary-jane LEE"   -> "Mary-Jane Lee"
 *   ""                -> ""
 */
fun normalizeName(raw: String): String {
    val collapsed = raw.trim().replace(Regex("\\s+"), " ")
    if (collapsed.isEmpty()) return ""
    return collapsed.split(" ").joinToString(" ") { word ->
        word.split("-").joinToString("-") { segment -> titleCaseSegment(segment) }
    }
}

private fun titleCaseSegment(segment: String): String {
    if (segment.isEmpty()) return segment
    val first = segment[0].uppercaseChar()
    val rest = segment.substring(1).lowercase()
    return "$first$rest"
}
