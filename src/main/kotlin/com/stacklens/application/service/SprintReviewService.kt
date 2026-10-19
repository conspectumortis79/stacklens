package com.stacklens.application.service

import com.stacklens.application.port.inbound.SprintReviewUseCase
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.knowledge.KnowledgePublication
import com.stacklens.application.service.sprintreview.SprintReviewLifecycle
import com.stacklens.application.service.sprintreview.SprintReviewOrchestrator
import com.stacklens.application.service.sprintreview.SprintReviewRunner
import com.stacklens.application.service.startup.StaleWorkHealer
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.application.stereotype.ManuallyWired
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.SprintReview
import org.slf4j.LoggerFactory
import java.util.concurrent.Executor

@ApplicationService
@ManuallyWired
class SprintReviewService
    constructor(
        private val deps: SprintReviewServiceDeps,
        private val reviewExecutor: Executor,
        private val knowledgePublication: com.stacklens.application.service.knowledge.KnowledgePublication,
        private val knowledgeMapper: com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper,
        private val lifecycle: SprintReviewLifecycle,
        private val orchestrator: SprintReviewOrchestrator,
        private val clock: ClockPort,
        private val runner: SprintReviewRunner =
            SprintReviewRunner(
                sprintReviewRepo = deps.sprintReviewRepo,
                jiraConfigRepo = deps.jiraConfigRepo,
                jira = deps.jira,
                copilot = deps.copilot,
                aiUsageContext = deps.aiUsageContext,
                i18n = deps.i18n,
                knowledgePublication = knowledgePublication,
                knowledgeMapper = knowledgeMapper,
                lifecycle = lifecycle,
                orchestrator = orchestrator,
                reviewExecutor = reviewExecutor,
                clock = clock,
            ),
    ) : SprintReviewUseCase,
        StaleWorkHealer {
        private val log = LoggerFactory.getLogger(SprintReviewService::class.java)

        private val sprintReviewRepo get() = deps.sprintReviewRepo
        private val i18n get() = deps.i18n
        private val asyncExecutor get() = deps.asyncExecutor
        private val startupHealer get() = deps.startupHealer

        private companion object {
            val STALE_REVIEW_STATUSES = setOf(AnalysisStatus.PENDING, AnalysisStatus.RUNNING)
        }

        override fun heal() {
            resetStaleReviews()
        }

        fun resetStaleReviews() =
            startupHealer.heal(
                logTag = "SprintReview",
                failedStatus = AnalysisStatus.CANCELLED,
                findStale = {
                    sprintReviewRepo
                        .findAllByOrderByCreatedAtDesc()
                        .filter { it.status in STALE_REVIEW_STATUSES }
                },
                markFailed = { review ->
                    review.status = AnalysisStatus.CANCELLED
                    review.errorMessage = i18n.t("sprint_review.be_aborted_restart")
                    review.completedAt = clock.localDateTime()
                },
                save = { sprintReviewRepo.saveSprintReview(it) },
            )

        override fun startReview(
            sprintName: String,
            targetTicketKey: String,
            jiraConfigId: Long?,
            templateOverride: String?,
            includeImprovement: Boolean,
        ): SprintReview {
            val saved =
                lifecycle.persistNewReview(
                    sprintName = sprintName,
                    targetTicketKey = targetTicketKey,
                    jiraConfigId = jiraConfigId,
                    templateOverride = templateOverride,
                    includeImprovement = includeImprovement,
                )
            asyncExecutor.submit("analysisTaskExecutor") { runReviewAsync(saved.id) }
            return saved
        }

        override fun getReview(id: Long): SprintReview? = lifecycle.getReview(id)

        override fun getAllReviews(): List<SprintReview> = lifecycle.getAllReviews()

        override fun cancelReview(id: Long) = lifecycle.cancelReview(id)

        override fun deleteReview(id: Long) = lifecycle.deleteReview(id)

        override fun deleteAllReviews() = lifecycle.deleteAllReviews()

        override fun firstActiveJiraConfigId(): Long? = lifecycle.firstActiveJiraConfigId()

        override fun jiraConfigExists(id: Long): Boolean = lifecycle.jiraConfigExists(id)

        override fun defaultPromptTemplate(): String = lifecycle.defaultPromptTemplate()

        override fun postAdfToJira(id: Long): SprintReview = runner.postAdfToJira(id)

        fun runReviewAsync(id: Long) = runner.runReviewAsync(id)

        fun runReview(id: Long) = runner.runReview(id)
    }
