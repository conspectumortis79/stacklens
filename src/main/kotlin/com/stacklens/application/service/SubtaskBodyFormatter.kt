package com.stacklens.application.service

object SubtaskBodyFormatter {
    const val CHECKMARK = "✅"
    const val GERMAN_LABEL = "AK"
    const val ENGLISH_LABEL = "AC"

    private const val MAX_MARKER_LENGTH = 60

    private val MARKDOWN_HEADING = Regex("^#{1,6}\\s+(.+)$")
    private val BOLD_LINE = Regex("^\\*\\*(.+?)\\*\\*[:.]?$")
    private val LIST_ITEM = Regex("^\\s*(?:[-*+]|\\d+[.)])\\s+")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}\\s]")
    private val WHITESPACE = Regex("\\s+")

    private val STORY_EXACT = setOf("story", "user story", "userstory", "anwendergeschichte")
    private val STORY_PREFIXES = listOf("user story", "userstory", "anwendergeschichte")
    private val ACCEPTANCE_EXACT = setOf("ac", "acs", "a c", "ak", "aks", "a k")
    private val ACCEPTANCE_PHRASES =
        listOf(
            "acceptance criteria",
            "acceptance criterion",
            "abnahmekriterien",
            "abnahme kriterien",
            "akzeptanzkriterien",
            "akzeptanz kriterien",
        )

    private val ACCEPTANCE_LABELS = mapOf("de" to GERMAN_LABEL, "en" to ENGLISH_LABEL)

    fun labelForLanguage(language: String?): String {
        val primary =
            language
                ?.trim()
                ?.lowercase()
                ?.substringBefore('-')
                .orEmpty()
        return ACCEPTANCE_LABELS[primary] ?: if (primary.isEmpty()) GERMAN_LABEL else ENGLISH_LABEL
    }

    fun format(
        description: String,
        acceptanceLabel: String = GERMAN_LABEL,
    ): String {
        if (description.isBlank()) return description
        val replacement = "$CHECKMARK $acceptanceLabel:"
        val kept =
            description
                .lineSequence()
                .map { line -> markerOf(line) to line }
                .filterNot { (marker, _) -> marker != null && isStoryMarker(marker) }
                .map { (marker, line) -> if (marker != null && isAcceptanceMarker(marker)) replacement else line }
                .joinToString("\n")
        return collapseBlankLines(kept).trim()
    }

    private fun markerOf(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith(">")) return null
        MARKDOWN_HEADING.matchEntire(trimmed)?.let { return marker(it.groupValues[1]) }
        BOLD_LINE.matchEntire(trimmed)?.let { return marker(it.groupValues[1]) }
        if (LIST_ITEM.containsMatchIn(trimmed)) return null
        return marker(trimmed)
    }

    private fun marker(text: String): String {
        val normalised =
            text
                .replace(NON_WORD, " ")
                .replace(WHITESPACE, " ")
                .trim()
                .lowercase()
        return if (normalised.length > MAX_MARKER_LENGTH) "\u0000" else normalised
    }

    private fun isStoryMarker(marker: String): Boolean {
        val prefixed = STORY_PREFIXES.any { marker.startsWith(it) }
        return marker in STORY_EXACT || prefixed
    }

    private fun isAcceptanceMarker(marker: String): Boolean {
        val phrased = ACCEPTANCE_PHRASES.any { marker.contains(it) }
        return marker in ACCEPTANCE_EXACT || phrased
    }

    private fun collapseBlankLines(text: String): String = text.replace(Regex("\n{3,}"), "\n\n")
}
