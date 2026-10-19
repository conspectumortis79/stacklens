package com.stacklens.application.service

import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.inbound.JiraTicketSummaryUseCase
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JiraTicketSummaryJiraPort
import com.stacklens.application.port.out.JiraTicketSummaryPort
import com.stacklens.application.port.out.JiraTicketSummaryTicket
import com.stacklens.application.port.out.MarkdownConvertPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.knowledge.KnowledgePublication
import com.stacklens.application.service.startup.StaleWorkHealer
import com.stacklens.application.service.ticketsummary.JiraTicketSummaryRunner
import com.stacklens.application.service.ticketsummary.TicketSummaryPromptBuilder
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.JiraTicketSummary
import org.slf4j.LoggerFactory

@ApplicationService
class JiraTicketSummaryService(
    private val summaryRepo: JiraTicketSummaryPort,
    private val jiraConfigRepo: JiraConfigPort,
    private val jira: JiraTicketSummaryJiraPort,
    private val copilot: CopilotPort,
    private val aiUsageContext: AiUsageContextPort,
    private val markdownConverter: MarkdownConvertPort,
    private val i18n: I18nPort,
    private val asyncExecutor: AsyncExecutorPort,
    private val knowledgePublication: com.stacklens.application.service.knowledge.KnowledgePublication,
    private val knowledgeMapper: com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper,
    private val startupHealer: StartupRecovery,
    private val promptBuilder: TicketSummaryPromptBuilder,
    private val clock: ClockPort,
    private val summaryRunner: JiraTicketSummaryRunner =
        JiraTicketSummaryRunner(
            summaryRepo = summaryRepo,
            jiraConfigRepo = jiraConfigRepo,
            jira = jira,
            copilot = copilot,
            aiUsageContext = aiUsageContext,
            markdownConverter = markdownConverter,
            i18n = i18n,
            knowledgePublication = knowledgePublication,
            knowledgeMapper = knowledgeMapper,
            promptBuilder = promptBuilder,
            clock = clock,
        ),
) : JiraTicketSummaryUseCase,
    StaleWorkHealer {
    private val log = LoggerFactory.getLogger(JiraTicketSummaryService::class.java)

    private companion object {
        val STALE_SUMMARY_STATUSES = setOf(AnalysisStatus.PENDING, AnalysisStatus.RUNNING)
    }

    override fun heal() {
        resetStaleSummaries()
    }

    fun resetStaleSummaries() =
        startupHealer.heal(
            logTag = "JiraTicketSummary",
            failedStatus = AnalysisStatus.CANCELLED,
            findStale = {
                summaryRepo
                    .findAllByOrderByCreatedAtDesc()
                    .filter { it.status in STALE_SUMMARY_STATUSES }
            },
            markFailed = { summary ->
                summary.status = AnalysisStatus.CANCELLED
                summary.errorMessage = i18n.tFor(copilot.resolveLanguage(), "ticket_summary.be_aborted_restart")
                summary.completedAt = clock.localDateTime()
            },
            save = { summaryRepo.saveJiraTicketSummary(it) },
        )

    override fun startSummary(
        ticketKey: String,
        jiraConfigId: Long?,
        templateOverride: String?,
    ): JiraTicketSummary {
        val key = ticketKey.trim()
        if (key.isBlank()) {
            throw IllegalArgumentException(
                i18n.tFor(copilot.resolveLanguage(), "ticket_summary.be_ticket_key_required"),
            )
        }
        val resolvedConfigId =
            jiraConfigId
                ?: jiraConfigRepo.findFirstByActiveTrue()?.id
                ?: throw IllegalStateException(
                    i18n.tFor(copilot.resolveLanguage(), "ticket_summary.be_no_jira_config"),
                )
        if (!jiraConfigRepo.jiraConfigExistsById(resolvedConfigId)) {
            throw IllegalStateException(
                i18n.tFor(copilot.resolveLanguage(), "ticket_summary.be_jira_config_not_found"),
            )
        }
        val cleanTemplate = templateOverride?.takeIf { it.isNotBlank() }
        val entity =
            JiraTicketSummary(
                ticketKey = key,
                jiraConfigId = resolvedConfigId,
                templateOverride = cleanTemplate,
            )
        val saved = summaryRepo.saveJiraTicketSummary(entity)
        asyncExecutor.submit("analysisTaskExecutor") { runSummary(saved.id) }
        return saved
    }

    override fun getSummary(id: Long): JiraTicketSummary? = summaryRepo.findJiraTicketSummaryById(id)

    override fun getAllSummaries(): List<JiraTicketSummary> = summaryRepo.findRecentJiraTicketSummaries(AnalysisConstants.MAX_RECENT_SUMMARIES)

    override fun cancelSummary(id: Long) {
        val e = summaryRepo.findJiraTicketSummaryById(id) ?: return
        if (e.status == AnalysisStatus.RUNNING || e.status == AnalysisStatus.PENDING) {
            e.status = AnalysisStatus.CANCELLED
            e.currentStep = i18n.tFor(copilot.resolveLanguage(), "ticket_summary.step_cancelled")
            e.completedAt = clock.localDateTime()
            runCatching { summaryRepo.saveJiraTicketSummary(e) }
        }
    }

    override fun deleteSummary(id: Long) = summaryRepo.deleteJiraTicketSummaryById(id)

    override fun deleteAllSummaries() = summaryRepo.deleteAllJiraTicketSummaries()

    override fun firstActiveJiraConfigId(): Long? = jiraConfigRepo.findFirstByActiveTrue()?.id

    override fun jiraConfigExists(id: Long): Boolean = jiraConfigRepo.jiraConfigExistsById(id)

    override fun defaultPromptTemplate(): String = promptBuilder.defaultTemplate()

    fun runSummary(id: Long) = summaryRunner.runSummary(id)

    internal fun buildOriginalText(ticket: JiraTicketSummaryTicket): String = summaryRunner.buildOriginalText(ticket)
}
