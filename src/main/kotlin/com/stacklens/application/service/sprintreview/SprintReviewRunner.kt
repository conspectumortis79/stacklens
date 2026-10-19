package com.stacklens.application.service.sprintreview

import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.ResolvedSprint
import com.stacklens.application.port.out.SprintReviewAdfDocument
import com.stacklens.application.port.out.SprintReviewJiraPort
import com.stacklens.application.port.out.SprintReviewPort
import com.stacklens.application.port.out.SprintReviewTicket
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper
import com.stacklens.application.service.knowledge.KnowledgePublication
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.EntityConcurrentlyModifiedException
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.SprintReview
import com.stacklens.domain.model.SprintReviewRow
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class SprintReviewRunner(
    private val sprintReviewRepo: SprintReviewPort,
    private val jiraConfigRepo: JiraConfigPort,
    private val jira: SprintReviewJiraPort,
    private val copilot: CopilotPort,
    private val aiUsageContext: AiUsageContextPort,
    private val i18n: I18nPort,
    private val knowledgePublication: KnowledgePublication,
    private val knowledgeMapper: AnalysisKnowledgeMapper,
    private val lifecycle: SprintReviewLifecycle,
    private val orchestrator: SprintReviewOrchestrator,
    private val reviewExecutor: Executor,
    private val clock: ClockPort,
) {
    private val log = LoggerFactory.getLogger(SprintReviewRunner::class.java)

    fun runReviewAsync(id: Long) {
        runReview(id)
    }

    fun runReview(id: Long) {
        val entity = sprintReviewRepo.findSprintReviewById(id) ?: return
        if (entity.status == AnalysisStatus.RUNNING) {
            log.warn("Sprint Review {} already in RUNNING state — skipping duplicate run", id)
            return
        }
        try {
            entity.status = AnalysisStatus.RUNNING
            entity.currentStep = t("step_resolve_sprint")
            sprintReviewRepo.saveSprintReview(entity)

            aiUsageContext.setContext("sprint_review", id)
            aiUsageContext.setModelOverride(copilot.resolveModel())

            val config =
                entity.jiraConfigId?.let { jiraConfigRepo.findJiraConfigById(it) }
                    ?: throw IllegalStateException(t("err_no_jira_config"))

            val ctx = ReviewContext(entity = entity, id = id, config = config)

            val sprint = resolveSprint(ctx)
            ctx.sprint = sprint
            entity.sprintId = sprint.id
            entity.sprintState = sprint.state
            entity.sprintUrl = sprint.url
            entity.currentStep = t("step_fetched_sprint", sprint.name)
            sprintReviewRepo.saveSprintReview(entity)

            ctx.tickets = fetchSprintTickets(ctx, sprint)
            entity.totalIssues = ctx.tickets.size
            entity.currentStep = t("step_loaded_tickets", ctx.tickets.size)
            sprintReviewRepo.saveSprintReview(entity)

            val rows = reviewAllTickets(ctx)

            buildAdfTable(ctx, rows)

            finaliseRun(ctx)

            entity.status = AnalysisStatus.COMPLETED
            entity.currentStep = t("step_done")
            entity.completedAt = clock.localDateTime()
            sprintReviewRepo.saveSprintReview(entity)

            runCatching {
                knowledgeMapper.fromSprintReview(entity)?.let { candidate ->
                    knowledgePublication.publish(candidate)
                }
            }.onFailure { log.warn("Knowledge publication failed for sprint review {}: {}", id, it.message) }
        } catch (e: EntityConcurrentlyModifiedException) {
            log.warn("Sprint Review {} aborted: concurrent modification detected", id)
        } catch (e: Exception) {
            log.warn("Sprint Review {} failed: {}", id, e.message)
            val fresh = runCatching { sprintReviewRepo.findSprintReviewById(id) }.getOrNull()
            if (fresh != null) {
                fresh.status = AnalysisStatus.FAILED
                fresh.errorMessage = i18n.translateError(e).take(2000)
                fresh.completedAt = clock.localDateTime()
                runCatching { sprintReviewRepo.saveSprintReview(fresh) }
                    .onFailure { ex -> log.warn("Sprint Review {} could not save FAILED state: {}", id, ex.message) }
            }
        } finally {
            aiUsageContext.clearContext()
        }
    }

    fun postAdfToJira(id: Long): SprintReview {
        val entity =
            sprintReviewRepo.findSprintReviewById(id)
                ?: throw IllegalArgumentException("sprint review $id not found")
        val targetKey = entity.targetTicketKey.trim()
        if (targetKey.isEmpty()) {
            throw IllegalArgumentException("Sprint review $id has no target ticket key")
        }
        val adfJson = entity.tableAdfJson
        if (adfJson.isNullOrBlank()) {
            throw IllegalStateException(
                "Sprint review $id has no ADF document — re-run the review first.",
            )
        }
        val config =
            entity.jiraConfigId?.let { jiraConfigRepo.findJiraConfigById(it) }
                ?: throw IllegalStateException(
                    "No Jira configuration linked to sprint review $id",
                )
        val postResult =
            runCatching { jira.appendAdfToDescription(config, targetKey, SprintReviewAdfDocument(adfJson)) }
        val updated =
            postResult.fold(
                onSuccess = {
                    log.info(
                        "[SprintReview] Posted ADF table to {} (sprint review {})",
                        targetKey,
                        id,
                    )
                    entity.errorMessage = null
                    val base = config.baseUrl.trimEnd('/')
                    entity.targetTicketUrl = "$base/browse/$targetKey"
                    entity.currentStep = t("step_done_posted")
                    sprintReviewRepo.saveSprintReview(entity)
                },
                onFailure = { failure ->
                    val reason = failure.message ?: "unknown"
                    log.warn("[SprintReview] Posting ADF to {} failed: {}", targetKey, failure.message)
                    entity.errorMessage = t("err_post_failed", reason)
                    sprintReviewRepo.saveSprintReview(entity)
                },
            )
        return updated
    }

    private class ReviewContext(
        val entity: SprintReview,
        val id: Long,
        val config: JiraConfig,
    ) {
        lateinit var sprint: ResolvedSprint
        lateinit var tickets: List<SprintReviewTicket>
    }

    private fun resolveSprint(ctx: ReviewContext): ResolvedSprint =
        runCatching {
            jira.resolveSprintByName(ctx.config, ctx.entity.sprintName)
        }.getOrElse { e ->
            throw IllegalStateException(
                t("err_resolve_sprint", ctx.entity.sprintName, e.message ?: "unknown"),
                e,
            )
        }

    private fun fetchSprintTickets(
        ctx: ReviewContext,
        sprint: ResolvedSprint,
    ): List<SprintReviewTicket> {
        val tickets = jira.fetchSprintTickets(ctx.config, sprint.id)
        if (tickets.isEmpty()) {
            throw IllegalStateException(t("err_sprint_has_no_tickets", sprint.name))
        }
        return tickets
    }

    private fun reviewAllTickets(ctx: ReviewContext): List<SprintReviewRow> {
        val template = ctx.entity.templateOverride ?: lifecycle.defaultPromptTemplate()
        val total = ctx.tickets.size
        if (total == 0) return emptyList()

        val trackKind = "sprint_review"
        val trackId = ctx.id
        val trackModel = copilot.resolveModel()

        val futures =
            ctx.tickets.map { ticket ->
                CompletableFuture.supplyAsync(
                    {
                        aiUsageContext.setContext(trackKind, trackId)
                        aiUsageContext.setModelOverride(trackModel)
                        try {
                            ticket to orchestrator.reviewTicket(template, ticket)
                        } finally {
                            aiUsageContext.clearContext()
                        }
                    },
                    reviewExecutor,
                )
            }

        val rows = mutableListOf<SprintReviewRow>()
        for ((idx, future) in futures.withIndex()) {
            val fresh = sprintReviewRepo.findSprintReviewById(ctx.id)
            if (fresh?.status == AnalysisStatus.CANCELLED) {
                for (j in idx until futures.size) futures[j].cancel(true)
                return rows
            }
            val (ticket, row) =
                try {
                    future.get(60, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    log.warn("[SprintReview {}] ticket {} failed: {}", ctx.id, ctx.tickets[idx].key, e.message)
                    continue
                }
            rows += row
            ctx.entity.currentStep =
                t("step_review_ticket") + " (${rows.size}/$total) ${ticket.key}"
            ctx.entity.rowsJson = lifecycle.encodeRows(rows.toList())
            sprintReviewRepo.saveSprintReview(ctx.entity)
        }
        return rows.toList()
    }

    private fun buildAdfTable(
        ctx: ReviewContext,
        rows: List<SprintReviewRow>,
    ) {
        ctx.entity.currentStep = t("step_build_table")
        sprintReviewRepo.saveSprintReview(ctx.entity)
        ctx.entity.tableAdfJson =
            runCatching {
                jira
                    .renderAdfDocument(
                        rows = rows,
                        includeImprovement = ctx.entity.includeImprovement,
                        sprintName = ctx.entity.sprintName,
                    ).json
            }.getOrNull()
    }

    private fun finaliseRun(ctx: ReviewContext) {
        ctx.entity.currentStep = t("step_await_post")
        sprintReviewRepo.saveSprintReview(ctx.entity)
        val base = ctx.config.baseUrl.trimEnd('/')
        ctx.entity.targetTicketUrl = "$base/browse/${ctx.entity.targetTicketKey}"
    }

    private fun t(key: String): String = i18n.tFor(copilot.resolveLanguage(), "sprint_review.$key")

    private fun t(
        key: String,
        vararg args: Any,
    ): String = i18n.tFor(copilot.resolveLanguage(), "sprint_review.$key", *args)
}
