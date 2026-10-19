package com.stacklens.application.port.inbound

import com.stacklens.domain.model.SprintReview

interface SprintReviewReadUseCase {
    fun getReview(id: Long): SprintReview?

    fun getAllReviews(): List<SprintReview>

    fun firstActiveJiraConfigId(): Long?

    fun jiraConfigExists(id: Long): Boolean

    fun defaultPromptTemplate(): String
}

interface SprintReviewWriteUseCase {
    fun startReview(
        sprintName: String,
        targetTicketKey: String,
        jiraConfigId: Long?,
        templateOverride: String?,
        includeImprovement: Boolean,
    ): SprintReview

    fun cancelReview(id: Long)

    fun deleteReview(id: Long)

    fun deleteAllReviews()

    fun postAdfToJira(id: Long): SprintReview
}
