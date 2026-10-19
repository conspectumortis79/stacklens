package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.TicketCreationRecordPort
import com.stacklens.domain.model.TicketCreationRecord
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface TicketCreationRecordRepository :
    JpaRepository<TicketCreationRecord, Long>,
    TicketCreationRecordPort {
    override fun findAllTicketCreationRecordsByCreatedAtDesc(): List<TicketCreationRecord> =
        findAll(
            org.springframework.data.domain.Sort
                .by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"),
        )

    override fun findRecentTicketCreationRecords(limit: Int): List<TicketCreationRecord> =
        findAll(
            PageRequest.of(
                0,
                limit,
                org.springframework.data.domain.Sort
                    .by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"),
            ),
        ).content

    @Query("SELECT e FROM TicketCreationRecord e WHERE e.id = :id")
    override fun findTicketCreationRecordById(
        @Param("id") id: Long,
    ): TicketCreationRecord?

    override fun saveTicketCreationRecord(record: TicketCreationRecord): TicketCreationRecord = save(record)

    override fun deleteTicketCreationRecordById(id: Long) = deleteById(id)

    override fun deleteAllTicketCreationRecords() = deleteAll()

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(
        "UPDATE TicketCreationRecord r " +
            "SET r.status = 'FAILED', " +
            "    r.errorMessage = CASE " +
            "        WHEN r.errorMessage IS NULL OR r.errorMessage = '' THEN :errorMessage " +
            "        ELSE CONCAT(r.errorMessage, '\n', :errorMessage) " +
            "    END " +
            "WHERE r.status = 'RUNNING'",
    )
    override fun markAllRunningAsFailed(
        @Param("errorMessage") errorMessage: String,
    ): Int
}
