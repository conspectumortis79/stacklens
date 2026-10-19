package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.JiraTicketImplementationPort
import com.stacklens.domain.model.JiraTicketImplementation
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface JiraTicketImplementationRepository :
    JpaRepository<JiraTicketImplementation, Long>,
    JiraTicketImplementationPort {
    override fun findAllByOrderByCreatedAtDesc(): List<JiraTicketImplementation>

    override fun findRecentJiraTicketImplementations(limit: Int): List<JiraTicketImplementation> = findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))).content

    @Query("SELECT e FROM JiraTicketImplementation e WHERE e.id = :id")
    override fun findJiraTicketImplementationById(
        @Param("id") id: Long,
    ): JiraTicketImplementation?

    override fun saveJiraTicketImplementation(analysis: JiraTicketImplementation): JiraTicketImplementation = save(analysis)

    override fun deleteJiraTicketImplementationById(id: Long) = deleteById(id)

    @Modifying
    @Query("DELETE FROM JiraTicketImplementation e WHERE e.id > 0 AND e.status <> 'RUNNING' AND e.status <> 'PENDING'")
    override fun deleteFinishedAnalyses(): Int

    override fun deleteAllJiraTicketImplementations() = deleteAll()
}
