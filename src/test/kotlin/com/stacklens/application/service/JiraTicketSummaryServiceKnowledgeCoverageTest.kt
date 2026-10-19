package com.stacklens.application.service

import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JiraTicketSummaryComment
import com.stacklens.application.port.out.JiraTicketSummaryJiraPort
import com.stacklens.application.port.out.JiraTicketSummaryPort
import com.stacklens.application.port.out.JiraTicketSummaryTicket
import com.stacklens.application.port.out.MarkdownConvertPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper
import com.stacklens.application.service.knowledge.KnowledgePublication
import com.stacklens.application.service.knowledge.KnowledgePublicationCandidate
import com.stacklens.application.service.ticketsummary.TicketSummaryPromptBuilder
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.JiraTicketSummary
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import com.stacklens.testsupport.InlineAsyncExecutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class JiraTicketSummaryServiceKnowledgeCoverageTest {
    private val repo: JiraTicketSummaryPort = mock()
    private val jiraConfigRepo: JiraConfigPort = mock()
    private val jira: JiraTicketSummaryJiraPort = mock()
    private val copilot: CopilotPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val i18n: I18nPort = mock()
    private val publication: KnowledgePublication = mock()
    private val mapper: AnalysisKnowledgeMapper = mock()

    private val service =
        JiraTicketSummaryService(
            summaryRepo = repo,
            jiraConfigRepo = jiraConfigRepo,
            jira = jira,
            copilot = copilot,
            aiUsageContext = mock(),
            promptBuilder = TicketSummaryPromptBuilder(promptLoader),
            markdownConverter =
                object : MarkdownConvertPort {
                    override fun sanitize(raw: String?): String = raw ?: ""

                    override fun render(markdown: String?): String = "<div>$markdown</div>"
                },
            i18n = i18n,
            asyncExecutor = InlineAsyncExecutor(),
            startupHealer = StartupHealer(),
            knowledgePublication = publication,
            knowledgeMapper = mapper,
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    private val candidate =
        KnowledgePublicationCandidate(
            sourceType = KnowledgeSourceType.TICKET_SUMMARY,
            externalId = "ticket-summary:1",
            title = "PROJ-1",
            sourceUri = null,
            content = "markdown",
            metadata = emptyMap(),
        )

    private fun stubRun(entity: JiraTicketSummary) {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        whenever(repo.findJiraTicketSummaryById(1L)).thenReturn(entity)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchTicketWithComments(any(), any())).thenReturn(
            JiraTicketSummaryTicket(
                key = "PROJ-1",
                summary = "sum",
                description = "desc",
                url = "https://j/browse/PROJ-1",
                status = "In Progress",
                issueType = "Story",
                priority = "High",
                assignee = "A",
                reporter = "R",
                labels = listOf("x"),
                createdAt = "2025-11-04",
                updatedAt = "2025-11-05",
                comments = listOf(JiraTicketSummaryComment("Alice", "one", "2025-11-05")),
            ),
        )
        whenever(promptLoader.get(any(), any(), any())).thenReturn("TEMPLATE")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("## Overview")
        whenever(i18n.tFor(any(), any())).thenAnswer { it.getArgument(1, String::class.java) }
        whenever(repo.saveJiraTicketSummary(any())).thenAnswer { it.getArgument(0, JiraTicketSummary::class.java) }
    }

    @Test
    fun `a finished summary with a knowledge candidate is published`() {
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 7L)
        stubRun(entity)
        whenever(mapper.fromTicketSummary(any())).thenReturn(candidate)

        service.runSummary(1L)

        org.mockito.kotlin
            .verify(publication)
            .publish(candidate)
        assertEquals(AnalysisStatus.COMPLETED, entity.status)
    }

    @Test
    fun `a publication failure leaves the finished summary untouched`() {
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 7L)
        stubRun(entity)
        whenever(mapper.fromTicketSummary(any())).thenReturn(candidate)
        whenever(publication.publish(any())).thenThrow(RuntimeException("the index is busy"))

        service.runSummary(1L)

        assertEquals(AnalysisStatus.COMPLETED, entity.status)
        assertNotNull(entity.completedAt)
    }

    @Test
    fun `a summary without a knowledge candidate skips the publication`() {
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 7L)
        stubRun(entity)
        whenever(mapper.fromTicketSummary(any())).thenReturn(null)

        service.runSummary(1L)

        verifyNoPublications()
    }

    private fun verifyNoPublications() {
        org.mockito.kotlin
            .verify(publication, org.mockito.kotlin.never())
            .publish(any())
    }
}
