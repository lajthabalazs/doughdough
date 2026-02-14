package ca.lajthabalazs.doughdough.data

import java.util.regex.Pattern

/**
 * Parses timing strings from the recipe format:
 * - Absolute: "16:00" (time of day, minutes from midnight)
 * - Relative: "+16h", "+3h", "+30m", "+2h", "+1h", "+20m"
 */
object TimeParser {
    private val RELATIVE_PATTERN = Pattern.compile("^\\+\\s*(\\d+)\\s*(h|m|min|hr|hour|hours|minutes?)$", Pattern.CASE_INSENSITIVE)
    private val ABSOLUTE_PATTERN = Pattern.compile("^(\\d{1,2}):(\\d{2})$")

    /**
     * Parse a duration string to milliseconds.
     * @param timeStr e.g. "+16h", "+30m", "16:00"
     * @param previousDurationMillis For absolute times, this is the previous step's end time (minutes from midnight * 60000)
     * @return Duration in milliseconds until this step from the previous step's completion
     */
    fun parseToDurationMillis(timeStr: String, previousDurationMillis: Long = 0): Long {
        val trimmed = timeStr.trim()
        if (trimmed.isBlank()) return 0L

        // Relative: +16h, +30m
        val relativeMatcher = RELATIVE_PATTERN.matcher(trimmed)
        if (relativeMatcher.matches()) {
            val value = relativeMatcher.group(1)!!.toLong()
            val unit = relativeMatcher.group(2)!!.lowercase()
            return when {
                unit.startsWith("h") -> value * 60 * 60 * 1000
                unit.startsWith("m") -> value * 60 * 1000
                else -> value * 60 * 60 * 1000 // default hours
            }
        }

        // Absolute: 16:00 - for first step (previous=0) means "do now"; otherwise hours:minutes from start
        val absoluteMatcher = ABSOLUTE_PATTERN.matcher(trimmed)
        if (absoluteMatcher.matches()) {
            if (previousDurationMillis == 0L) return 0L  // First step: do immediately
            val hours = absoluteMatcher.group(1)!!.toLong()
            val minutes = absoluteMatcher.group(2)!!.toLong()
            val totalMinutes = hours * 60 + minutes
            val absoluteMs = totalMinutes * 60 * 1000
            return (absoluteMs - previousDurationMillis).coerceAtLeast(0)
        }

        return 0L
    }

    /**
     * Parse a step's timing and return its duration from the previous step.
     * Also returns the cumulative time for use with the next step.
     */
    fun parseStep(
        timeStr: String,
        title: String,
        description: String,
        previousCumulativeMs: Long
    ): Pair<RecipeStep, Long> {
        val duration = parseToDurationMillis(timeStr, previousCumulativeMs)
        val step = RecipeStep(
            startTime = timeStr,
            title = title,
            description = description,
            durationMillis = duration
        )
        val newCumulative = previousCumulativeMs + duration
        return step to newCumulative
    }
}
