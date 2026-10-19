package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.TicketRefinementPort
import com.stacklens.domain.EntityConcurrentlyModifiedException
import com.stacklens.domain.model.TicketRefinement
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Repository

@Repository
interface TicketRefinementRepository :
    JpaRepository<TicketRefinement, Long>,
    TicketRefinementPort {
    override fun findAllByOrderByCreatedAtDesc(): List<TicketRefinement>

    override fun findRecentTicketRefinements(limit: Int): List<TicketRefinement> = findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))).content

    @Query("SELECT e FROM TicketRefinement e WHERE e.id = :id")
    override fun findTicketRefinementById(
        @Param("id") id: Long,
    ): TicketRefinement?

    override fun saveTicketRefinement(entity: TicketRefinement): TicketRefinement =
        try {
            save(entity)
        } catch (e: ObjectOptimisticLockingFailureException) {
            throw EntityConcurrentlyModifiedException("TicketRefinement ${entity.id}")
        }

    override fun deleteTicketRefinementById(id: Long) = deleteById(id)

    override fun deleteAllTicketRefinements() = deleteAll()
}
