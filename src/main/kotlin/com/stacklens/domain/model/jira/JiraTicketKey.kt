package com.stacklens.domain.model.jira

import java.util.regex.Pattern

@JvmInline
value class JiraTicketKey(
    val value: String,
) {
    init {
        require(value.isNotBlank()) {
            "Jira-Key darf nicht leer sein (Format: PROJ-123 oder eine vollständige Ticket-URL wie https://company.atlassian.net/browse/PROJ-123)."
        }
        require(PATTERN.matcher(value).matches()) {
            "Ungültiger Jira-Key. Format: PROJ-123 oder eine vollständige Ticket-URL wie https://company.atlassian.net/browse/PROJ-123."
        }
    }

    val projectKey: String
        get() = value.substringBeforeLast('-')

    val number: String
        get() = value.substringAfterLast('-', missingDelimiterValue = "")

    override fun toString(): String = value

    companion object {
        private val PATTERN: Pattern = Pattern.compile("^([A-Z][A-Z0-9_]+-\\d+)(/[A-Za-z0-9_.-]+)?$")

        fun extract(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val bare = trimmed.substringAfter("/browse/", missingDelimiterValue = trimmed)
            val candidate = bare.substringBefore('/', missingDelimiterValue = bare)
            return if (PATTERN.matcher(candidate).matches()) candidate else null
        }

        fun of(raw: String): JiraTicketKey = JiraTicketKey(raw.trim())
    }
}
