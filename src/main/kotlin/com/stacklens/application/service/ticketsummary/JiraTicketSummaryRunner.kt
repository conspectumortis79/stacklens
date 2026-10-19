package com.stacklens.application.service.ticketsummary

import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JiraTicketSummaryJiraPort
import com.stacklens.application.port.out.JiraTicketSummaryPort
import com.stacklens.application.port.out.JiraTicketSummaryTicket
import com.stacklens.application.port.out.MarkdownConvertPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper
import com.stacklens.application.service.knowledge.KnowledgePublication
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.EntityConcurrentlyModifiedException
import com.stacklens.domain.model.JiraTicketSummary
import org.slf4j.LoggerFactory

class JiraTicketSummaryRunner(
    private val summaryRepo: JiraTicketSummaryPort,
    private val jiraConfigRepo: JiraConfigPort,
    private val jira: JiraTicketSummaryJiraPort,
    private val copilot: CopilotPort,
    private val aiUsageContext: AiUsageContextPort,
    private val markdownConverter: MarkdownConvertPort,
    private val i18n: I18nPort,
    private val knowledgePublication: KnowledgePublication,
    private val knowledgeMapper: AnalysisKnowledgeMapper,
    private val promptBuilder: TicketSummaryPromptBuilder,
    private val clock: ClockPort,
) {
    private val log = LoggerFactory.getLogger(JiraTicketSummaryRunner::class.java)

    private data class RenderedSummary(
        val markdown: String,
        val html: String,
    )

    fun runSummary(id: Long) {
        val entity = summaryRepo.findJiraTicketSummaryById(id) ?: return
        if (entity.status == AnalysisStatus.RUNNING) {
            log.warn("Ticket Summary {} already in RUNNING state — skipping duplicate run", id)
            return
        }
        try {
            summarize(entity, id)
        } catch (e: EntityConcurrentlyModifiedException) {
            log.warn("Ticket Summary {} aborted: concurrent modification detected", id)
        } catch (e: Exception) {
            markFailed(id, e)
        } finally {
            aiUsageContext.clearContext()
        }
    }

    private fun summarize(
        entity: JiraTicketSummary,
        id: Long,
    ) {
        markRunning(entity)
        val ticket = fetchTicket(entity)
        if (isCancelled(id)) return
        markInvokeAiStep(entity, ticket)
        if (isCancelled(id)) return
        val result = invokeAi(entity, id, ticket)
        if (isCancelled(id)) return
        markCompleted(entity, result)
        publishToKnowledge(entity, id)
    }

    private fun markRunning(entity: JiraTicketSummary) {
        entity.status = AnalysisStatus.RUNNING
        entity.currentStep = i18n.tFor(copilot.resolveLanguage(), "ticket_summary.step_fetch_ticket")
        summaryRepo.saveJiraTicketSummary(entity)
    }

    private fun fetchTicket(entity: JiraTicketSummary): JiraTicketSummaryTicket {
        val config =
            entity.jiraConfigId?.let { jiraConfigRepo.findJiraConfigById(it) }
                ?: throw IllegalStateException(i18n.tFor(copilot.resolveLanguage(), "ticket_summary.err_no_jira_config"))
        return jira.fetchTicketWithComments(config, entity.ticketKey)
            ?: throw IllegalStateException(i18n.tFor(copilot.resolveLanguage(), "ticket_summary.err_ticket_not_found"))
    }

    private fun markInvokeAiStep(
        entity: JiraTicketSummary,
        ticket: JiraTicketSummaryTicket,
    ) {
        entity.originalText = buildOriginalText(ticket)
        entity.currentStep = i18n.tFor(copilot.resolveLanguage(), "ticket_summary.step_invoke_ai")
        summaryRepo.saveJiraTicketSummary(entity)
    }

    private fun invokeAi(
        entity: JiraTicketSummary,
        id: Long,
        ticket: JiraTicketSummaryTicket,
    ): RenderedSummary {
        val template = entity.templateOverride ?: promptBuilder.defaultTemplate()
        val model = copilot.resolveModel()
        val prompt = promptBuilder.build(template, ticket, copilot.resolveLanguage())
        aiUsageContext.setContext("ticket_summary", id)
        aiUsageContext.setModelOverride(model)
        val markdown = markdownConverter.sanitize(copilot.execute(model, prompt))
        return RenderedSummary(markdown, markdownConverter.render(markdown))
    }

    private fun markCompleted(
        entity: JiraTicketSummary,
        result: RenderedSummary,
    ) {
        entity.summaryMarkdown = result.markdown
        entity.summaryHtml = result.html
        entity.status = AnalysisStatus.COMPLETED
        entity.currentStep = i18n.tFor(copilot.resolveLanguage(), "ticket_summary.step_done")
        entity.completedAt = clock.localDateTime()
        summaryRepo.saveJiraTicketSummary(entity)
    }

    private fun publishToKnowledge(
        entity: JiraTicketSummary,
        id: Long,
    ) {
        runCatching {
            knowledgeMapper.fromTicketSummary(entity)?.let { candidate ->
                knowledgePublication.publish(candidate)
            }
        }.onFailure { log.warn("Knowledge publication failed for ticket summary {}: {}", id, it.message) }
    }

    private fun markFailed(
        id: Long,
        cause: Exception,
    ) {
        log.warn("Ticket Summary {} failed: {}", id, cause.message)
        val fresh =
            try {
                summaryRepo.findJiraTicketSummaryById(id)
            } catch (ex: Exception) {
                log.warn("Ticket Summary {} could not re-fetch entity: {}", id, ex.message)
                null
            } ?: return
        fresh.status = AnalysisStatus.FAILED
        fresh.errorMessage = i18n.translateError(cause)
        fresh.completedAt = clock.localDateTime()
        runCatching { summaryRepo.saveJiraTicketSummary(fresh) }
            .onFailure { log.warn("Ticket Summary {} could not save FAILED state: {}", id, it.message) }
    }

    private fun isCancelled(id: Long): Boolean = runCatching { summaryRepo.findJiraTicketSummaryById(id)?.status == AnalysisStatus.CANCELLED }.getOrDefault(false)

    fun buildOriginalText(ticket: JiraTicketSummaryTicket): String =
        buildString {
            appendLine("# ${ticket.key} — ${ticket.summary}")
            appendLine()
            appendLine(
                "**Typ:** ${ticket.issueType} · **Status:** ${ticket.status}" +
                    if (ticket.priority.isNotBlank()) " · **Priorität:** ${ticket.priority}" else "",
            )
            if (ticket.assignee.isNotBlank()) appendLine("**Bearbeiter:** ${ticket.assignee}")
            if (ticket.reporter.isNotBlank()) appendLine("**Ersteller:** ${ticket.reporter}")
            if (ticket.labels.isNotEmpty()) appendLine("**Labels:** ${ticket.labels.joinToString(", ")}")
            appendLine()
            appendLine("## Beschreibung")
            appendLine()
            appendLine(ticket.description.ifBlank { "(leer)" })
            appendLine()
            if (ticket.comments.isNotEmpty()) {
                appendLine("## Kommentare (${ticket.comments.size})")
                appendLine()
                ticket.comments.forEachIndexed { i, c ->
                    appendLine(
                        "### ${i + 1}. ${c.author.ifBlank { "Unbekannt" }}" +
                            if (c.createdAt.isNotBlank()) " — ${c.createdAt}" else "",
                    )
                    appendLine()
                    appendLine(c.body)
                    appendLine()
                }
            }
        }.trim()
}
