package com.stacklens.application.service.sprintreview

import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JsonWriterPort
import com.stacklens.application.port.out.SprintReviewPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.AnalysisConstants
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.SprintReview

@ApplicationService
class SprintReviewLifecycle(
    private val sprintReviewRepo: SprintReviewPort,
    private val jiraConfigRepo: JiraConfigPort,
    private val jsonCodec: JsonWriterPort,
    private val i18n: I18nPort,
    private val copilot: CopilotPort,
    private val promptLoader: PromptReaderPort,
    private val clock: ClockPort,
) {
    fun persistNewReview(
        sprintName: String,
        targetTicketKey: String,
        jiraConfigId: Long?,
        templateOverride: String?,
        includeImprovement: Boolean,
    ): SprintReview {
        require(sprintName.isNotBlank()) { "sprintName must not be blank" }
        require(targetTicketKey.isNotBlank()) { "targetTicketKey must not be blank" }
        val resolvedConfigId = jiraConfigId ?: jiraConfigRepo.findFirstByActiveTrue()?.id
        val entity =
            SprintReview(
                sprintName = sprintName.trim(),
                targetTicketKey = targetTicketKey.trim(),
                jiraConfigId = resolvedConfigId,
                templateOverride = templateOverride?.takeIf { it.isNotBlank() },
                includeImprovement = includeImprovement,
                status = AnalysisStatus.PENDING,
            )
        return sprintReviewRepo.saveSprintReview(entity)
    }

    fun getReview(id: Long): SprintReview? = sprintReviewRepo.findSprintReviewById(id)

    fun getAllReviews(): List<SprintReview> = sprintReviewRepo.findRecentSprintReviews(AnalysisConstants.MAX_RECENT_REVIEWS)

    fun cancelReview(id: Long) {
        val entity = sprintReviewRepo.findSprintReviewById(id) ?: return
        if (entity.status == AnalysisStatus.RUNNING || entity.status == AnalysisStatus.PENDING) {
            entity.status = AnalysisStatus.CANCELLED
            entity.errorMessage = i18n.t("sprint_review.be_cancelled")
            entity.completedAt = clock.localDateTime()
            sprintReviewRepo.saveSprintReview(entity)
        }
    }

    fun deleteReview(id: Long) = sprintReviewRepo.deleteSprintReviewById(id)

    fun deleteAllReviews() = sprintReviewRepo.deleteAllSprintReviews()

    fun firstActiveJiraConfigId(): Long? = jiraConfigRepo.findFirstByActiveTrue()?.id

    fun jiraConfigExists(id: Long): Boolean = jiraConfigRepo.jiraConfigExistsById(id)

    fun defaultPromptTemplate(): String = promptLoader.get(copilot.resolveLanguage(), "sprintreview.review_ticket", emptyMap())

    fun save(entity: SprintReview): SprintReview = sprintReviewRepo.saveSprintReview(entity)

    fun markFailed(
        id: Long,
        errorMessage: String,
    ) {
        val fresh = sprintReviewRepo.findSprintReviewById(id) ?: return
        fresh.status = AnalysisStatus.FAILED
        fresh.errorMessage = errorMessage
        fresh.completedAt = clock.localDateTime()
        runCatching { sprintReviewRepo.saveSprintReview(fresh) }
    }

    fun currentState(id: Long): SprintReview? = sprintReviewRepo.findSprintReviewById(id)

    internal fun encodeRows(rows: List<com.stacklens.domain.model.SprintReviewRow>): String? = runCatching { jsonCodec.encode(rows) }.getOrNull()
}
