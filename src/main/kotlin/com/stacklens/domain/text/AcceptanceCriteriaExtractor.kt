package com.stacklens.domain.text

object AcceptanceCriteriaExtractor {
    fun extract(description: String): List<String> {
        val headerPattern =
            Regex(
                """(?i)(?:acceptance[\s\-]?criteria|akzeptanz(?:kriterien)?|abnahmekriterien|\bAK\b|\bACs?\b)\s*[:\n]""",
            )
        val match = headerPattern.find(description) ?: return emptyList()
        val afterHeader = description.substring(match.range.last + 1)
        val nextSection = Regex("""(?m)^(?:#{1,3}\s|h[1-4]\.|[A-Z][A-Za-z ]{3,}:)""").find(afterHeader)
        val section = if (nextSection != null) afterHeader.substring(0, nextSection.range.first) else afterHeader.take(1500)
        return section
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                line
                    .removePrefix("- [ ] ")
                    .removePrefix("- [x] ")
                    .removePrefix("- [X] ")
                    .removePrefix("[ ] ")
                    .removePrefix("[x] ")
                    .removePrefix("[X] ")
                    .removePrefix("* ")
                    .removePrefix("- ")
                    .trimStart { c -> c.isDigit() || c == '.' || c == ')' || c == ' ' }
                    .trim()
            }.filter { it.length > 3 }
            .take(20)
    }
}
