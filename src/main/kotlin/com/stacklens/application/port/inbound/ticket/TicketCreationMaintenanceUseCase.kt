package com.stacklens.application.port.inbound.ticket

interface TicketCreationMaintenanceUseCase {
    fun reapStaleRunningRecords(): Int
}
