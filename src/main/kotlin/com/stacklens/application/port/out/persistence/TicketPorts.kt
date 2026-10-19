package com.stacklens.application.port.out

import com.stacklens.domain.model.ConfluenceTool
import com.stacklens.domain.model.TicketCreationRecord
import com.stacklens.domain.model.TicketRefinement

interface TicketRefinementPort {
    fun findAllByOrderByCreatedAtDesc(): List<TicketRefinement>

    fun findRecentTicketRefinements(limit: Int): List<TicketRefinement>

    fun findTicketRefinementById(id: Long): TicketRefinement?

    fun saveTicketRefinement(entity: TicketRefinement): TicketRefinement

    fun deleteTicketRefinementById(id: Long)

    fun deleteAllTicketRefinements()
}

interface ConfluenceToolPort {
    fun findAllByOrderByCreatedAtDesc(): List<ConfluenceTool>

    fun findRecentConfluenceTools(limit: Int): List<ConfluenceTool>

    fun findConfluenceToolById(id: Long): ConfluenceTool?

    fun saveConfluenceTool(tool: ConfluenceTool): ConfluenceTool

    fun deleteConfluenceTool(tool: ConfluenceTool)

    fun deleteAllConfluenceTools()
}

interface TicketCreationRecordPort {
    fun findAllTicketCreationRecordsByCreatedAtDesc(): List<TicketCreationRecord>

    fun findRecentTicketCreationRecords(limit: Int): List<TicketCreationRecord>

    fun findTicketCreationRecordById(id: Long): TicketCreationRecord?

    fun saveTicketCreationRecord(record: TicketCreationRecord): TicketCreationRecord

    fun deleteTicketCreationRecordById(id: Long)

    fun deleteAllTicketCreationRecords()

    fun markAllRunningAsFailed(errorMessage: String): Int
}
