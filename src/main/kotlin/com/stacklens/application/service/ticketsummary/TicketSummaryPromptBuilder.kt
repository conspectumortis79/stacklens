package com.stacklens.application.service.ticketsummary

import com.stacklens.application.port.out.JiraTicketSummaryTicket
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class TicketSummaryPromptBuilder(
    private val promptLoader: PromptReaderPort,
) {
    private val languageInstructions =
        mapOf(
            "de" to
                "**IMPORTANT: Write your entire response in German (Deutsch).** This overrides any language instruction in the template below.",
        )

    fun defaultTemplate(): String = promptLoader.get("en", "ticketsummary.summarize_ticket", emptyMap())

    fun build(
        template: String,
        ticket: JiraTicketSummaryTicket,
        language: String?,
    ): String =
        buildString {
            languageInstructions[language]?.let { instruction ->
                appendLine(instruction)
                appendLine()
            }
            appendLine(template)
            appendLine()
            appendLine("## TICKET METADATA")
            appendLine()
            appendLine("- Key: ${ticket.key}")
            appendLine("- Summary: ${ticket.summary}")
            appendLine("- Issue Type: ${ticket.issueType}")
            appendLine("- Status: ${ticket.status}")
            if (ticket.priority.isNotBlank()) appendLine("- Priority: ${ticket.priority}")
            if (ticket.assignee.isNotBlank()) appendLine("- Assignee: ${ticket.assignee}")
            if (ticket.reporter.isNotBlank()) appendLine("- Reporter: ${ticket.reporter}")
            if (ticket.labels.isNotEmpty()) appendLine("- Labels: ${ticket.labels.joinToString(", ")}")
            if (ticket.createdAt.isNotBlank()) appendLine("- Created: ${ticket.createdAt}")
            if (ticket.updatedAt.isNotBlank()) appendLine("- Updated: ${ticket.updatedAt}")
            if (ticket.url.isNotBlank()) appendLine("- URL: ${ticket.url}")
            appendLine()
            appendLine("## DESCRIPTION (verbatim)")
            appendLine()
            appendLine(ticket.description.ifBlank { "(empty)" })
            appendLine()
            appendComments(ticket)
        }

    private fun StringBuilder.appendComments(ticket: JiraTicketSummaryTicket) {
        if (ticket.comments.isEmpty()) return
        appendLine("## COMMENTS (chronological, ${ticket.comments.size})")
        appendLine()
        ticket.comments.forEachIndexed { index, comment ->
            appendLine(
                "### Comment ${index + 1} — ${comment.author.ifBlank { "Unbekannt" }}" +
                    if (comment.createdAt.isNotBlank()) " (${comment.createdAt})" else "",
            )
            appendLine()
            appendLine(comment.body)
            appendLine()
        }
    }
}
