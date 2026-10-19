package com.stacklens.application.service.ticketcreation

import com.stacklens.application.port.inbound.ticket.TicketCreationMaintenanceUseCase
import com.stacklens.application.port.out.TicketCreationRecordPort
import com.stacklens.application.stereotype.ApplicationService
import org.slf4j.LoggerFactory

@ApplicationService
class TicketCreationMaintenanceService(
    private val recordPort: TicketCreationRecordPort,
) : TicketCreationMaintenanceUseCase {
    private val log = LoggerFactory.getLogger(TicketCreationMaintenanceService::class.java)

    override fun reapStaleRunningRecords(): Int {
        val attempt =
            runCatching {
                recordPort.markAllRunningAsFailed(
                    "Generation was interrupted by an unclean JVM shutdown. Please retry.",
                )
            }.onFailure { log.warn("[TicketCreationReaper] failed to clean up stale RUNNING records (non-fatal): {}", it.message) }
        val affected = attempt.getOrDefault(0)
        if (affected > 0) {
            log.info(
                "[TicketCreationReaper] marked {} stale RUNNING record(s) as FAILED (JVM was stopped mid-generation)",
                affected,
            )
        } else {
            log.debug("[TicketCreationReaper] no stale RUNNING records found")
        }
        return affected
    }
}
