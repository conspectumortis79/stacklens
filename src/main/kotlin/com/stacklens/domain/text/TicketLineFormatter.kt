package com.stacklens.domain.text

import com.stacklens.domain.model.jira.TicketSummaryLine

object TicketLineFormatter {
    const val EMPTY_PLACEHOLDER: String = "(none)"

    fun format(items: List<TicketSummaryLine>): String {
        if (items.isEmpty()) return EMPTY_PLACEHOLDER
        return items.joinToString("\n\n") { line ->
            val description = line.description.trim()
            if (description.isNotBlank()) "**${line.key}** — ${line.summary}\n$description" else "**${line.key}** — ${line.summary}"
        }
    }
}
