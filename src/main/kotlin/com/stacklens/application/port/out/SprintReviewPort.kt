package com.stacklens.application.port.out

import com.stacklens.domain.model.SprintReview

interface SprintReviewPort {
    fun findAllByOrderByCreatedAtDesc(): List<SprintReview>

    fun findRecentSprintReviews(limit: Int): List<SprintReview>

    fun findSprintReviewById(id: Long): SprintReview?

    fun saveSprintReview(entity: SprintReview): SprintReview

    fun deleteSprintReviewById(id: Long)

    fun deleteAllSprintReviews()
}
