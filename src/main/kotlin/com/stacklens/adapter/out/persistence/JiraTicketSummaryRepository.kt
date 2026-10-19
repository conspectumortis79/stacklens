package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.JiraTicketSummaryPort
import com.stacklens.domain.EntityConcurrentlyModifiedException
import com.stacklens.domain.model.JiraTicketSummary
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Repository

@Repository
interface JiraTicketSummaryRepository :
    JpaRepository<JiraTicketSummary, Long>,
    JiraTicketSummaryPort {
    override fun findAllByOrderByCreatedAtDesc(): List<JiraTicketSummary>

    override fun findRecentJiraTicketSummaries(limit: Int): List<JiraTicketSummary> = findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))).content

    @Query("SELECT e FROM JiraTicketSummary e WHERE e.id = :id")
    override fun findJiraTicketSummaryById(
        @Param("id") id: Long,
    ): JiraTicketSummary?

    override fun saveJiraTicketSummary(entity: JiraTicketSummary): JiraTicketSummary =
        try {
            save(entity)
        } catch (e: ObjectOptimisticLockingFailureException) {
            throw EntityConcurrentlyModifiedException("JiraTicketSummary ${entity.id}")
        }

    override fun deleteJiraTicketSummaryById(id: Long) = deleteById(id)

    override fun deleteAllJiraTicketSummaries() = deleteAll()
}
