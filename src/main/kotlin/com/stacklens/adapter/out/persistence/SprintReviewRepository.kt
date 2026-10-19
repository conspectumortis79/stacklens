package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.SprintReviewPort
import com.stacklens.domain.EntityConcurrentlyModifiedException
import com.stacklens.domain.model.SprintReview
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Repository

@Repository
interface SprintReviewRepository :
    JpaRepository<SprintReview, Long>,
    SprintReviewPort {
    override fun findAllByOrderByCreatedAtDesc(): List<SprintReview>

    override fun findRecentSprintReviews(limit: Int): List<SprintReview> = findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))).content

    @Query("SELECT e FROM SprintReview e WHERE e.id = :id")
    override fun findSprintReviewById(
        @Param("id") id: Long,
    ): SprintReview?

    override fun saveSprintReview(entity: SprintReview): SprintReview =
        try {
            save(entity)
        } catch (e: ObjectOptimisticLockingFailureException) {
            val conflictId = e.identifier?.toString() ?: entity.id.toString()
            throw EntityConcurrentlyModifiedException("SprintReview $conflictId")
        }

    override fun deleteSprintReviewById(id: Long) = deleteById(id)

    override fun deleteAllSprintReviews() = deleteAll()
}
