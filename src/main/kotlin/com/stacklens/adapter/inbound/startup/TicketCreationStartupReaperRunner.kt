package com.stacklens.adapter.inbound.startup

import com.stacklens.application.port.inbound.ticket.TicketCreationMaintenanceUseCase
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(10)
@ConditionalOnProperty(
    name = ["stacklens.ticket-creation.reaper.startup"],
    matchIfMissing = true,
    havingValue = "true",
)
class TicketCreationStartupReaperRunner(
    private val maintenance: TicketCreationMaintenanceUseCase,
) : CommandLineRunner {
    override fun run(vararg args: String?) {
        runCatching { maintenance.reapStaleRunningRecords() }
    }
}
