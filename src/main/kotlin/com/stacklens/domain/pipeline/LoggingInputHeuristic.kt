package com.stacklens.domain.pipeline

private val STACK_TRACE_MARKERS: List<String> =
    listOf(
        ".java:",
        "Caused by:",
        "Exception:",
        "Traceback (most recent call last):",
        "panic:",
        "stack overflow",
    )

private val STACK_FRAME_REGEX: Regex = Regex("""^\s*at\s+[\w$.]+\(.*\)""")

private val ISO_DATE_REGEX: Regex = Regex("""\d{4}-\d{2}-\d{2}""")

private val LANGUAGE_NEUTRAL_SKIP_TOKENS: List<String> =
    listOf(
        LoggingInputHeuristic.STAGE_SKIP_TOKEN,
        LoggingInputHeuristic.DIAGNOSIS_NOT_POSSIBLE_TOKEN,
    )

object LoggingInputHeuristic {
    const val MIN_NON_BLANK_LINES = 5

    const val STAGE_SKIP_TOKEN: String = "DIAGNOSIS_INPUT_TOO_THIN"

    const val DIAGNOSIS_NOT_POSSIBLE_TOKEN: String = "DIAGNOSIS_NOT_POSSIBLE"

    enum class Reason {
        LINE_COUNT_BELOW_MINIMUM,
        NO_STACK_TRACE_MARKER,
        NO_ISO_DATE,
    }

    data class Verdict(
        val tooThin: Boolean,
        val reasons: List<Reason>,
        val nonBlankLineCount: Int,
        val hasStackTraceMarker: Boolean,
        val hasIsoDate: Boolean,
    )

    fun isStageSkipMarker(
        text: String,
        localizedSignatures: List<String>,
    ): Boolean = (LANGUAGE_NEUTRAL_SKIP_TOKENS + localizedSignatures).any { text.contains(it, ignoreCase = true) }

    fun classify(text: String): Verdict {
        val nonBlankLines = text.lines().count { it.isNotBlank() }
        val hasStackTraceMarker =
            STACK_TRACE_MARKERS.any { text.contains(it) } ||
                STACK_FRAME_REGEX.containsMatchIn(text)
        val hasIsoDate = ISO_DATE_REGEX.containsMatchIn(text)

        val reasons = mutableListOf<Reason>()
        if (nonBlankLines < MIN_NON_BLANK_LINES) {
            reasons += Reason.LINE_COUNT_BELOW_MINIMUM
        }
        if (!hasStackTraceMarker) {
            reasons += Reason.NO_STACK_TRACE_MARKER
        }
        if (!hasIsoDate) {
            reasons += Reason.NO_ISO_DATE
        }

        val tooThin = nonBlankLines < MIN_NON_BLANK_LINES && !hasStackTraceMarker && !hasIsoDate

        return Verdict(
            tooThin = tooThin,
            reasons = reasons,
            nonBlankLineCount = nonBlankLines,
            hasStackTraceMarker = hasStackTraceMarker,
            hasIsoDate = hasIsoDate,
        )
    }
}
