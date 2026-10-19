
package com.stacklens.application.service.ticketrefinement

import com.stacklens.application.port.out.TicketRefinementTicket
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.text.TicketLineFormatter

interface TicketFieldFormatting {
    fun formatSubtasks(ticket: TicketRefinementTicket): String

    fun formatReferencedTickets(ticket: TicketRefinementTicket): String

    fun formatLabels(ticket: TicketRefinementTicket): String
}

@ApplicationService
class TicketFieldFormatter : TicketFieldFormatting {
    override fun formatSubtasks(ticket: TicketRefinementTicket): String = TicketLineFormatter.format(ticket.subtasks)

    override fun formatReferencedTickets(ticket: TicketRefinementTicket): String = TicketLineFormatter.format(ticket.referencedTickets)

    override fun formatLabels(ticket: TicketRefinementTicket): String = if (ticket.labels.isEmpty()) "(none)" else ticket.labels.joinToString(", ")
}
