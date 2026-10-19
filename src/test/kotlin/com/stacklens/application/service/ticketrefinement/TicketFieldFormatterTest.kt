package com.stacklens.application.service.ticketrefinement

import com.stacklens.application.port.out.TicketRefinementSubtask
import com.stacklens.application.port.out.TicketRefinementTicket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TicketFieldFormatterTest {
    private val formatter = TicketFieldFormatter()

    private fun ticket(
        subtasks: List<TicketRefinementSubtask> = emptyList(),
        referencedTickets: List<TicketRefinementSubtask> = emptyList(),
        labels: List<String> = emptyList(),
    ): TicketRefinementTicket =
        TicketRefinementTicket(
            key = "K",
            summary = "S",
            description = "D",
            url = "https://jira/K",
            subtasks = subtasks,
            referencedTickets = referencedTickets,
            labels = labels,
        )

    private fun sub(
        key: String = "K-1",
        summary: String = "Sub",
        description: String = "",
    ): TicketRefinementSubtask = TicketRefinementSubtask(key = key, summary = summary, description = description)

    @Test
    fun `formatSubtasks returns none for an empty list`() {
        assertEquals("(none)", formatter.formatSubtasks(ticket()))
    }

    @Test
    fun `formatSubtasks renders a single subtask without description`() {
        val t = ticket(subtasks = listOf(sub(description = "")))
        assertEquals("**K-1** — Sub", formatter.formatSubtasks(t))
    }

    @Test
    fun `formatSubtasks renders a single subtask with description`() {
        val t = ticket(subtasks = listOf(sub(description = "Details")))
        assertEquals("**K-1** — Sub\nDetails", formatter.formatSubtasks(t))
    }

    @Test
    fun `formatSubtasks renders multiple subtasks separated by blank lines`() {
        val t =
            ticket(
                subtasks =
                    listOf(
                        sub(key = "K-1", summary = "S1", description = "D1"),
                        sub(key = "K-2", summary = "S2", description = "D2"),
                    ),
            )
        assertEquals("**K-1** — S1\nD1\n\n**K-2** — S2\nD2", formatter.formatSubtasks(t))
    }

    @Test
    fun `formatReferencedTickets returns none for an empty list`() {
        assertEquals("(none)", formatter.formatReferencedTickets(ticket()))
    }

    @Test
    fun `formatReferencedTickets renders a single ticket`() {
        val t = ticket(referencedTickets = listOf(sub(description = "Details")))
        assertEquals("**K-1** — Sub\nDetails", formatter.formatReferencedTickets(t))
    }

    @Test
    fun `formatReferencedTickets renders a single ticket without description`() {
        val t = ticket(referencedTickets = listOf(sub(description = "")))
        assertEquals("**K-1** — Sub", formatter.formatReferencedTickets(t))
    }

    @Test
    fun `formatLabels returns none for an empty list`() {
        assertEquals("(none)", formatter.formatLabels(ticket()))
    }

    @Test
    fun `formatLabels joins labels with comma+space`() {
        val t = ticket(labels = listOf("a", "b", "c"))
        assertEquals("a, b, c", formatter.formatLabels(t))
    }
}
