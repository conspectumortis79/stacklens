package com.stacklens.application.service

import com.stacklens.application.port.inbound.RepoSelection
import com.stacklens.application.port.inbound.TicketRefinementUseCase
import com.stacklens.application.port.model.SubtaskDraft
import com.stacklens.application.port.out.TicketRefinementTicket
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.knowledge.KnowledgePublication
import com.stacklens.application.service.startup.StaleWorkHealer
import com.stacklens.application.service.ticketrefinement.TicketRefinementCollaborators
import com.stacklens.application.service.ticketrefinement.TicketRefinementDeps
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.application.stereotype.ManuallyWired
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.EntityConcurrentlyModifiedException
import com.stacklens.domain.model.TicketRefinement
import com.stacklens.domain.model.TicketReview
import org.slf4j.LoggerFactory

@ApplicationService
@ManuallyWired
class TicketRefinementService
    constructor(
        private val deps: TicketRefinementDeps,
        private val knowledgePublication: com.stacklens.application.service.knowledge.KnowledgePublication,
        private val knowledgeMapper: com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper,
        private val collaborators: TicketRefinementCollaborators,
        private val clock: ClockPort,
    ) : TicketRefinementUseCase,
        StaleWorkHealer {
        private val log = LoggerFactory.getLogger(TicketRefinementService::class.java)

        private val ticketRefinementRepo get() = deps.ticketRefinementRepo
        private val jiraConfigRepo get() = deps.jiraConfigRepo
        private val jira get() = deps.jira
        private val copilot get() = deps.copilot
        private val aiUsageContext get() = deps.aiUsageContext
        private val promptLoader get() = deps.promptLoader
        private val jsonCodec get() = deps.jsonCodec
        private val i18n get() = deps.i18n
        private val dbConfigRepo get() = deps.dbConfigRepo
        private val dbSchemaPort get() = deps.dbSchemaPort
        private val asyncExecutor get() = deps.asyncExecutor
        private val startupHealer get() = deps.startupHealer

        private val fieldFormatter = collaborators.fieldFormatter
        private val reviewEngine = collaborators.reviewEngine
        private val repoAnalyser = collaborators.repoAnalyser
        private val commentPoster = collaborators.commentPoster
        private val lifecycle = collaborators.lifecycle
        private val subtaskCoordinator = collaborators.subtaskCoordinator

        override fun heal() {
            resetStaleRefinements()
        }

        fun resetStaleRefinements() =
            startupHealer.heal(
                logTag = "TicketRefinement",
                failedStatus = AnalysisStatus.CANCELLED,
                findStale = {
                    ticketRefinementRepo
                        .findAllByOrderByCreatedAtDesc()
                        .filter { it.status in STALE_REFINEMENT_STATUSES }
                },
                markFailed = { refinement ->
                    refinement.status = AnalysisStatus.CANCELLED
                    refinement.errorMessage = i18n.t("ticket_refinement.be_aborted_restart")
                    refinement.completedAt = clock.localDateTime()
                },
                save = { ticketRefinementRepo.saveTicketRefinement(it) },
            )

        override fun startPreparation(
            parentTicketKey: String,
            jiraConfigId: Long?,
            templateOverride: String?,
            repoSelections: List<RepoSelection>,
            singleTicketOnly: Boolean,
            dbConfigIds: List<Long>,
        ): TicketRefinement {
            val saved =
                lifecycle.persistNewPreparation(
                    parentTicketKey,
                    jiraConfigId,
                    templateOverride,
                    repoSelections,
                    singleTicketOnly,
                    dbConfigIds,
                )
            asyncExecutor.submit("analysisTaskExecutor") { runPreparationAsync(saved.id) }
            return saved
        }

        override fun getPreparation(id: Long): TicketRefinement? = lifecycle.getPreparation(id)

        override fun getAllPreparations(): List<TicketRefinement> = lifecycle.getAllPreparations()

        override fun cancelPreparation(id: Long) = lifecycle.cancelPreparation(id)

        override fun deletePreparation(id: Long) = lifecycle.deletePreparation(id)

        override fun deleteAllPreparations() = lifecycle.deleteAllPreparations()

        override fun firstActiveJiraConfigId(): Long? = lifecycle.firstActiveJiraConfigId()

        override fun jiraConfigExists(id: Long): Boolean = lifecycle.jiraConfigExists(id)

        override fun defaultPromptTemplate(): String = promptLoader.get(copilot.resolveLanguage(), "ticketrefinement.improve_ticket", emptyMap())

        override fun postReviewAsComment(
            preparationId: Long,
            ticketKey: String,
        ): TicketRefinement {
            val updated =
                commentPoster.postReviewAsComment(preparationId, ticketKey) {
                    ticketRefinementRepo.findTicketRefinementById(preparationId)
                }
            return ticketRefinementRepo.saveTicketRefinement(updated)
        }

        override fun createSuggestedSubtasks(
            preparationId: Long,
            ticketKey: String,
            subtasks: List<SubtaskDraft>,
        ): Map<String, Any?> = subtaskCoordinator.createSuggestedSubtasks(preparationId, ticketKey, subtasks)

        fun runPreparationAsync(id: Long) {
            runPreparation(id)
        }

        fun runPreparation(id: Long) {
            val entity = ticketRefinementRepo.findTicketRefinementById(id) ?: return
            if (entity.status == AnalysisStatus.RUNNING) {
                log.warn("Ticket Refinement {} already in RUNNING state — skipping duplicate run", id)
                return
            }
            try {
                entity.status = AnalysisStatus.RUNNING
                entity.currentStep = t("step_fetch_parent")
                ticketRefinementRepo.saveTicketRefinement(entity)

                aiUsageContext.setContext("ticket_refinement", id)
                aiUsageContext.setModelOverride(copilot.resolveModel())

                val ctx = buildPreparationContext(entity)

                if (reviewAllChildren(ctx) is ReviewOutcome.Cancelled) return

                finalizeCompletedRun(ctx.entity)
            } catch (e: EntityConcurrentlyModifiedException) {
                log.warn("Ticket Refinement {} aborted: concurrent modification detected", id)
            } catch (e: Exception) {
                log.warn("Ticket Refinement {} failed: {}", id, e.message)
                val fresh = runCatching { ticketRefinementRepo.findTicketRefinementById(id) }.getOrNull()
                if (fresh != null) {
                    fresh.status = AnalysisStatus.FAILED
                    fresh.errorMessage = i18n.translateError(e)
                    fresh.completedAt = clock.localDateTime()
                    runCatching { ticketRefinementRepo.saveTicketRefinement(fresh) }
                        .onFailure { ex -> log.warn("Ticket Refinement {} could not save FAILED state: {}", id, ex.message) }
                }
            } finally {
                aiUsageContext.clearContext()
            }
        }

        private class PreparationContext(
            val entity: TicketRefinement,
            val id: Long,
            val parent: TicketRefinementTicket,
            val children: List<TicketRefinementTicket>,
            val template: String,
            val repoSelections: List<RepoSelection>,
            val dbSchema: String,
        )

        private sealed class ReviewOutcome {
            data object Completed : ReviewOutcome()

            data object Cancelled : ReviewOutcome()
        }

        private fun buildPreparationContext(entity: TicketRefinement): PreparationContext {
            val config =
                entity.jiraConfigId?.let { jiraConfigRepo.findJiraConfigById(it) }
                    ?: throw IllegalStateException(t("err_no_jira_config"))
            val tickets = jira.fetchParentWithLinkedTickets(config, entity.parentTicketKey)
            if (tickets.isEmpty()) throw IllegalStateException(t("err_parent_not_found"))
            val parent = tickets.first()
            val children = if (entity.singleTicketOnly) listOf(parent) else tickets.drop(1)
            entity.parentTicketJson = jsonCodec.encode(parent)
            entity.currentStep = t("step_fetched_parent")
            ticketRefinementRepo.saveTicketRefinement(entity)

            val template = entity.templateOverride ?: defaultPromptTemplate()
            val repoSelections = repoAnalyser.parseRepoSelections(entity.repoSelectionsJson)
            val effectiveDbIds = resolveDbIds(entity)
            val dbSchema = loadDbSchema(effectiveDbIds, entity.id)
            return PreparationContext(
                entity = entity,
                id = entity.id,
                parent = parent,
                children = children,
                template = template,
                repoSelections = repoSelections,
                dbSchema = dbSchema,
            )
        }

        private fun reviewAllChildren(ctx: PreparationContext): ReviewOutcome {
            val reviews = mutableListOf<TicketReview>()
            for ((idx, child) in ctx.children.withIndex()) {
                val fresh = ticketRefinementRepo.findTicketRefinementById(ctx.id)
                if (fresh?.status == AnalysisStatus.CANCELLED) {
                    return ReviewOutcome.Cancelled
                }
                ctx.entity.currentStep =
                    t("step_review_ticket") + " (${idx + 1}/${ctx.children.size}) ${child.key}"
                ticketRefinementRepo.saveTicketRefinement(ctx.entity)
                var review = reviewEngine.reviewSingleTicket(ctx.template, ctx.parent, child, ctx.dbSchema)
                if (ctx.repoSelections.isNotEmpty() && review.state != "FAILED") {
                    ctx.entity.currentStep =
                        t("step_inspect_repos") + " (${idx + 1}/${ctx.children.size}) ${child.key}"
                    ticketRefinementRepo.saveTicketRefinement(ctx.entity)
                    val analyses = repoAnalyser.analyzeRepositories(ctx.parent, child, ctx.repoSelections)
                    review = review.copy(repoAnalyses = analyses)
                }
                reviews.add(review)
                ctx.entity.reviewsJson = jsonCodec.encode(reviews)
                ticketRefinementRepo.saveTicketRefinement(ctx.entity)
            }
            return ReviewOutcome.Completed
        }

        private fun finalizeCompletedRun(entity: TicketRefinement) {
            entity.status = AnalysisStatus.COMPLETED
            entity.currentStep = t("step_done")
            entity.completedAt = clock.localDateTime()
            ticketRefinementRepo.saveTicketRefinement(entity)
            runCatching {
                knowledgeMapper.fromTicketRefinement(entity).forEach { candidate ->
                    knowledgePublication.publish(candidate)
                }
            }.onFailure { log.warn("Knowledge publication failed for ticket refinement {}: {}", entity.id, it.message) }
        }

        private fun resolveDbIds(entity: TicketRefinement): List<Long> {
            val rawIds = entity.databaseConfigIds
            if (rawIds != null) {
                val fromNew = rawIds.split(",").mapNotNull { it.trim().toLongOrNull() }
                if (fromNew.isNotEmpty()) return fromNew
            }
            return listOfNotNull(entity.databaseConfigId)
        }

        private fun loadDbSchema(
            dbConfigIds: List<Long>,
            runId: Long,
        ): String {
            if (dbConfigIds.isEmpty()) return ""
            val dbConfigs = runCatching { dbConfigRepo.findAllDatabaseConfigsByIds(dbConfigIds) }.getOrNull().orEmpty()
            val schemaBuilder = StringBuilder()
            for (dbConfig in dbConfigs) {
                val schema =
                    runCatching { dbSchemaPort.fetchSchemaSummary(dbConfig) }.getOrElse { e ->
                        log.warn("Ticket Refinement {} DB schema fetch failed for config '{}': {}", runId, dbConfig.name, e.message)
                        ""
                    }
                if (schema.isNotBlank()) {
                    if (schemaBuilder.isNotEmpty()) schemaBuilder.append("\n\n---\n\n")
                    schemaBuilder.append("## Database: ${dbConfig.name}\n\n").append(schema)
                }
            }
            if (dbConfigs.isEmpty()) {
                log.warn("Ticket Refinement {} databaseConfigIds={} not found — skipping schema fetch", runId, dbConfigIds)
            }
            return schemaBuilder.toString()
        }

        internal fun parseAiResponse(
            raw: String,
            sourceFormat: DescriptionFormat = DescriptionFormat.MARKDOWN,
        ) = reviewEngine.parseAiResponse(raw, sourceFormat)

        internal fun sanitizeAiJson(s: String) = reviewEngine.sanitizeAiJson(s)

        internal fun parseRepoAnalysisResponse(
            raw: String,
            repoRef: String,
            repoName: String,
            repoType: String,
        ) = repoAnalyser.parseRepoAnalysisResponse(raw, repoRef, repoName, repoType)

        internal fun prependGreetingWiki(
            wiki: String,
            openPoints: List<String> = emptyList(),
        ): String = commentPoster.prependGreetingWiki(wiki, openPoints)

        internal fun formatSubtasks(ticket: TicketRefinementTicket): String = fieldFormatter.formatSubtasks(ticket)

        internal fun formatReferencedTickets(ticket: TicketRefinementTicket): String = fieldFormatter.formatReferencedTickets(ticket)

        internal fun formatLabels(ticket: TicketRefinementTicket): String = fieldFormatter.formatLabels(ticket)

        override fun normalizeReviewsJson(json: String?): String? = reviewEngine.normalizeReviewsJson(json)

        private fun t(key: String): String = i18n.tFor(copilot.resolveLanguage(), "ticket_refinement.$key")

        private companion object {
            val STALE_REFINEMENT_STATUSES = setOf(AnalysisStatus.PENDING, AnalysisStatus.RUNNING)
        }
    }
