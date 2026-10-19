package com.stacklens.application.service

import com.stacklens.application.port.out.TicketCreationRecordPort
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.TicketCreationRecord
import org.slf4j.LoggerFactory

class TicketCreationHistoryService(
    private val recordPort: TicketCreationRecordPort,
) {
    private val log = LoggerFactory.getLogger(TicketCreationHistoryService::class.java)

    fun saveFailedAttempt(
        inputText: String,
        errorMessage: String,
    ) {
        recordPort.saveTicketCreationRecord(
            TicketCreationRecord(
                inputText = inputText.take(2000),
                status = AnalysisStatus.FAILED,
                errorMessage = errorMessage.take(2000),
            ),
        )
    }

    fun getAllRecords(): List<TicketCreationRecord> = recordPort.findRecentTicketCreationRecords(AnalysisConstants.MAX_RECENT_RECORDS)

    fun getRecord(id: Long): TicketCreationRecord? = recordPort.findTicketCreationRecordById(id)

    fun cancelRecord(id: Long): Boolean {
        val record = recordPort.findTicketCreationRecordById(id) ?: return false
        if (record.status != "RUNNING") return false
        recordPort.saveTicketCreationRecord(
            TicketCreationRecord(
                id = record.id,
                inputText = record.inputText,
                status = AnalysisStatus.CANCELLED,
            ),
        )
        log.info("[TicketCreation] Record {} cancelled by user.", id)
        return true
    }

    fun deleteRecord(id: Long) = recordPort.deleteTicketCreationRecordById(id)

    fun deleteAllRecords() = recordPort.deleteAllTicketCreationRecords()
}
