package com.stacklens.application.port.out.ticket

interface TicketTextFetchPort {
    fun fetchTicketAsText(
        ticketKey: String,
        jiraConfigId: Long,
    ): String
}
