package com.stacklens.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JiraTicketSummaryComment
import com.stacklens.application.port.out.JiraTicketSummaryJiraPort
import com.stacklens.application.port.out.JiraTicketSummaryPort
import com.stacklens.application.port.out.JiraTicketSummaryTicket
import com.stacklens.application.port.out.MarkdownConvertPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.service.ticketsummary.TicketSummaryPromptBuilder
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.EntityConcurrentlyModifiedException
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.JiraTicketSummary
import com.stacklens.testsupport.FakeKnowledgePublicationPort
import com.stacklens.testsupport.InlineAsyncExecutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class JiraTicketSummaryServiceTest {
    private val repo = mock<JiraTicketSummaryPort>()
    private val jiraConfigRepo = mock<JiraConfigPort>()
    private val jira = mock<JiraTicketSummaryJiraPort>()
    private val copilot = mock<CopilotPort>()
    private val promptLoader = mock<PromptReaderPort>()
    private val i18n = mock<I18nPort>()
    private val asyncExecutor = InlineAsyncExecutor()
    private val objectMapper: ObjectMapper = ObjectMapper()
    private val markdownConverter: MarkdownConvertPort =
        object : MarkdownConvertPort {
            override fun sanitize(raw: String?): String = raw ?: ""

            override fun render(markdown: String?): String = "<div>$markdown</div>"
        }

    private val service =
        JiraTicketSummaryService(
            summaryRepo = repo,
            jiraConfigRepo = jiraConfigRepo,
            jira = jira,
            copilot = copilot,
            aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
            promptBuilder = TicketSummaryPromptBuilder(promptLoader),
            markdownConverter = markdownConverter,
            i18n = i18n,
            asyncExecutor = asyncExecutor,
            startupHealer = StartupHealer(),
            knowledgePublication = FakeKnowledgePublicationPort(),
            knowledgeMapper = mock<com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper>(),
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    @BeforeEach
    fun setUp() {
        whenever(copilot.resolveLanguage()).thenReturn("de")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.tFor(eq("de"), any<String>())).thenAnswer { it.getArgument(1, String::class.java) }
        whenever(repo.saveJiraTicketSummary(any())).thenAnswer {
            it.getArgument(0, JiraTicketSummary::class.java)
        }
    }

    @Test
    fun `resetStaleSummaries marks RUNNING and PENDING rows as CANCELLED`() {
        val running =
            JiraTicketSummary(
                id = 1L,
                ticketKey = "RUN-1",
                status = AnalysisStatus.RUNNING,
            )
        val pending =
            JiraTicketSummary(
                id = 2L,
                ticketKey = "PEN-1",
                status = AnalysisStatus.PENDING,
            )
        whenever(repo.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(running, pending))

        service.heal()

        assertEquals(AnalysisStatus.CANCELLED, running.status)
        assertNotNull(running.errorMessage)
        assertNotNull(running.completedAt)
        verify(repo, atLeastOnce()).saveJiraTicketSummary(any())
    }

    @Test
    fun `resetStaleSummaries ignores COMPLETED FAILED and CANCELLED rows`() {
        val completed = JiraTicketSummary(id = 1L, ticketKey = "C-1", status = AnalysisStatus.COMPLETED)
        val failed = JiraTicketSummary(id = 2L, ticketKey = "F-1", status = AnalysisStatus.FAILED)
        val cancelled = JiraTicketSummary(id = 3L, ticketKey = "X-1", status = AnalysisStatus.CANCELLED)
        whenever(repo.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(completed, failed, cancelled))

        service.resetStaleSummaries()

        assertEquals(AnalysisStatus.COMPLETED, completed.status)
        assertEquals(AnalysisStatus.FAILED, failed.status)
        assertEquals(AnalysisStatus.CANCELLED, cancelled.status)
        verify(repo, never()).saveJiraTicketSummary(any())
    }

    @Test
    fun `resetStaleSummaries does nothing when the repository throws`() {
        whenever(repo.findAllByOrderByCreatedAtDesc()).thenThrow(RuntimeException("DB down"))

        service.resetStaleSummaries()
    }

    @Test
    fun `startSummary throws IllegalArgumentException for blank key`() {
        try {
            service.startSummary("   ", null, null)
            org.junit.jupiter.api.Assertions
                .fail<Any>("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
        }
        verify(repo, never()).saveJiraTicketSummary(any())
    }

    @Test
    fun `startSummary throws IllegalStateException when no jira config exists`() {
        whenever(jiraConfigRepo.findFirstByActiveTrue()).thenReturn(null)
        try {
            service.startSummary("PROJ-1", null, null)
            org.junit.jupiter.api.Assertions
                .fail<Any>("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun `startSummary throws IllegalStateException when explicit configId does not exist`() {
        whenever(jiraConfigRepo.jiraConfigExistsById(99L)).thenReturn(false)
        try {
            service.startSummary("PROJ-1", 99L, null)
            org.junit.jupiter.api.Assertions
                .fail<Any>("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertNotNull(e.message)
        }
        verify(repo, never()).saveJiraTicketSummary(any())
    }

    @Test
    fun `startSummary persists a new PENDING row and dispatches the async run`() {
        val cfg = JiraConfig(id = 5L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        whenever(jiraConfigRepo.findFirstByActiveTrue()).thenReturn(cfg)
        whenever(jiraConfigRepo.jiraConfigExistsById(5L)).thenReturn(true)
        val saved = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 5L)
        whenever(repo.saveJiraTicketSummary(any())).thenReturn(saved)

        val result = service.startSummary("PROJ-1", null, null)

        assertEquals(1L, result.id)
        assertEquals("PROJ-1", result.ticketKey)
        assertEquals(5L, result.jiraConfigId)
        assertEquals(AnalysisStatus.PENDING, result.status)
        verify(repo).findJiraTicketSummaryById(1L)
    }

    @Test
    fun `startSummary strips a blank template override to null before persisting`() {
        val cfg = JiraConfig(id = 5L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        whenever(jiraConfigRepo.findFirstByActiveTrue()).thenReturn(cfg)
        whenever(jiraConfigRepo.jiraConfigExistsById(5L)).thenReturn(true)
        val saved = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 5L, templateOverride = null)
        whenever(repo.saveJiraTicketSummary(any())).thenReturn(saved)

        service.startSummary("PROJ-1", null, "   ")

        val arg = org.mockito.kotlin.argumentCaptor<JiraTicketSummary>()
        verify(repo).saveJiraTicketSummary(arg.capture())
        assertNull(arg.firstValue.templateOverride)
    }

    @Test
    fun `startSummary trims the ticketKey before persisting`() {
        val cfg = JiraConfig(id = 5L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        whenever(jiraConfigRepo.findFirstByActiveTrue()).thenReturn(cfg)
        whenever(jiraConfigRepo.jiraConfigExistsById(5L)).thenReturn(true)
        val saved = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 5L)
        whenever(repo.saveJiraTicketSummary(any())).thenReturn(saved)

        val result = service.startSummary("  PROJ-1  ", null, null)
        assertEquals("PROJ-1", result.ticketKey)
    }

    @Test
    fun `getSummary delegates to the port`() {
        val e = JiraTicketSummary(id = 7L, ticketKey = "PROJ-1")
        whenever(repo.findJiraTicketSummaryById(7L)).thenReturn(e)
        assertSame(e, service.getSummary(7L))
    }

    @Test
    fun `getAllSummaries delegates to the port`() {
        val list = listOf(JiraTicketSummary(id = 1L, ticketKey = "X"))
        whenever(repo.findRecentJiraTicketSummaries(200)).thenReturn(list)
        assertSame(list, service.getAllSummaries())
    }

    @Test
    fun `cancelSummary does nothing when entity is not found`() {
        whenever(repo.findJiraTicketSummaryById(99L)).thenReturn(null)
        service.cancelSummary(99L)
        verify(repo, never()).saveJiraTicketSummary(any())
    }

    @Test
    fun `cancelSummary marks RUNNING entity as CANCELLED and persists it`() {
        val e = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", status = AnalysisStatus.RUNNING)
        whenever(repo.findJiraTicketSummaryById(1L)).thenReturn(e)
        service.cancelSummary(1L)
        assertEquals(AnalysisStatus.CANCELLED, e.status)
        assertNotNull(e.currentStep)
        assertNotNull(e.completedAt)
        verify(repo).saveJiraTicketSummary(e)
    }

    @Test
    fun `cancelSummary marks PENDING entity as CANCELLED and persists it`() {
        val e = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", status = AnalysisStatus.PENDING)
        whenever(repo.findJiraTicketSummaryById(1L)).thenReturn(e)
        service.cancelSummary(1L)
        assertEquals(AnalysisStatus.CANCELLED, e.status)
        verify(repo).saveJiraTicketSummary(e)
    }

    @Test
    fun `cancelSummary is a no-op for COMPLETED entity`() {
        val e = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", status = AnalysisStatus.COMPLETED)
        whenever(repo.findJiraTicketSummaryById(1L)).thenReturn(e)
        service.cancelSummary(1L)
        assertEquals(AnalysisStatus.COMPLETED, e.status)
        verify(repo, never()).saveJiraTicketSummary(any())
    }

    @Test
    fun `deleteSummary delegates to the port`() {
        service.deleteSummary(7L)
        verify(repo).deleteJiraTicketSummaryById(7L)
    }

    @Test
    fun `deleteAllSummaries delegates to the port`() {
        service.deleteAllSummaries()
        verify(repo).deleteAllJiraTicketSummaries()
    }

    @Test
    fun `firstActiveJiraConfigId returns the id of the first active jira config`() {
        val cfg = JiraConfig(id = 9L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        whenever(jiraConfigRepo.findFirstByActiveTrue()).thenReturn(cfg)
        assertEquals(9L, service.firstActiveJiraConfigId())
    }

    @Test
    fun `firstActiveJiraConfigId returns null when no active config exists`() {
        whenever(jiraConfigRepo.findFirstByActiveTrue()).thenReturn(null)
        assertNull(service.firstActiveJiraConfigId())
    }

    @Test
    fun `jiraConfigExists delegates to the port`() {
        whenever(jiraConfigRepo.jiraConfigExistsById(11L)).thenReturn(true)
        assertTrue(service.jiraConfigExists(11L))
    }

    @Test
    fun `defaultPromptTemplate delegates to promptLoader with the right key`() {
        whenever(promptLoader.get("en", "ticketsummary.summarize_ticket", emptyMap()))
            .thenReturn("DEFAULT TPL")
        assertEquals("DEFAULT TPL", service.defaultPromptTemplate())
    }

    private fun ticketWithComments(
        key: String = "PROJ-1",
        description: String = "long description",
        comments: List<JiraTicketSummaryComment> = emptyList(),
    ) = JiraTicketSummaryTicket(
        key = key,
        summary = "summary text",
        description = description,
        url = "https://j/browse/$key",
        status = "In Progress",
        issueType = "Story",
        priority = "High",
        assignee = "Sarah Keller",
        reporter = "Markus Weber",
        labels = listOf("a", "b"),
        createdAt = "2025-11-04T09:00:00.000+0000",
        updatedAt = "2025-11-05T09:00:00.000+0000",
        comments = comments,
    )

    @Test
    fun `runSummary happy path completes and stores markdown + html on the entity`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 7L)
        whenever(repo.findJiraTicketSummaryById(1L)).thenReturn(entity)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        val ticket =
            ticketWithComments(
                comments =
                    listOf(
                        JiraTicketSummaryComment("Alice", "comment one", "2025-11-04T10:00:00.000+0000"),
                    ),
            )
        whenever(jira.fetchTicketWithComments(cfg, "PROJ-1")).thenReturn(ticket)
        whenever(promptLoader.get("en", "ticketsummary.summarize_ticket", emptyMap()))
            .thenReturn("PROMPT TEMPLATE")
        whenever(copilot.execute(eq("gpt-4"), any<String>(), anyOrNull(), anyOrNull())).thenReturn("## Overview\n\nsummary")

        service.runSummary(1L)

        assertEquals("## Overview\n\nsummary", entity.summaryMarkdown)
        assertNotNull(entity.summaryHtml)

        assertTrue(entity.summaryHtml!!.contains("<div>"))
        assertEquals(AnalysisStatus.COMPLETED, entity.status)
        assertNotNull(entity.completedAt)
        verify(repo, atLeastOnce()).saveJiraTicketSummary(entity)
    }

    @Test
    fun `runSummary uses the user-provided templateOverride when present`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity =
            JiraTicketSummary(
                id = 1L,
                ticketKey = "PROJ-1",
                jiraConfigId = 7L,
                templateOverride = "USER TPL",
            )
        whenever(repo.findJiraTicketSummaryById(1L)).thenReturn(entity)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchTicketWithComments(cfg, "PROJ-1")).thenReturn(ticketWithComments())
        whenever(copilot.execute(eq("gpt-4"), any<String>(), anyOrNull(), anyOrNull())).thenReturn("ok")

        service.runSummary(1L)

        verify(promptLoader, never()).get(any<String>(), eq("ticketsummary.summarize_ticket"), any())
        val promptCaptor = org.mockito.kotlin.argumentCaptor<String>()
        verify(copilot).execute(eq("gpt-4"), promptCaptor.capture(), anyOrNull<Long>(), anyOrNull())
        assertTrue(promptCaptor.firstValue.contains("USER TPL"))
    }

    @Test
    fun `runSummary returns silently when the entity is missing`() {
        whenever(repo.findJiraTicketSummaryById(99L)).thenReturn(null)
        service.runSummary(99L)
        verify(jira, never()).fetchTicketWithComments(any(), any())
        verify(copilot, never()).execute(any<String>(), any<String>(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `runSummary skips the run when entity is already RUNNING`() {
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", status = AnalysisStatus.RUNNING)
        whenever(repo.findJiraTicketSummaryById(1L)).thenReturn(entity)
        service.runSummary(1L)
        verify(jira, never()).fetchTicketWithComments(any(), any())
    }

    @Test
    fun `runSummary marks FAILED when no jira config is found for the entity`() {
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 99L)
        whenever(repo.findJiraTicketSummaryById(1L)).thenReturn(entity)
        whenever(jiraConfigRepo.findJiraConfigById(99L)).thenReturn(null)
        whenever(i18n.translateError(any())).thenReturn("no config")

        service.runSummary(1L)

        assertEquals(AnalysisStatus.FAILED, entity.status)
        assertEquals("no config", entity.errorMessage)
        assertNotNull(entity.completedAt)
    }

    @Test
    fun `runSummary marks FAILED when the ticket is not found in jira`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 7L)
        whenever(repo.findJiraTicketSummaryById(1L)).thenReturn(entity)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchTicketWithComments(cfg, "PROJ-1")).thenReturn(null)
        whenever(i18n.translateError(any())).thenReturn("ticket not found")

        service.runSummary(1L)

        assertEquals(AnalysisStatus.FAILED, entity.status)
        assertEquals("ticket not found", entity.errorMessage)
    }

    @Test
    fun `runSummary bails out when the entity is cancelled between the jira fetch and the ai call`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 7L)
        val cancelled =
            JiraTicketSummary(
                id = 1L,
                ticketKey = "PROJ-1",
                jiraConfigId = 7L,
                status = AnalysisStatus.CANCELLED,
            )
        whenever(repo.findJiraTicketSummaryById(1L))
            .thenReturn(entity)
            .thenReturn(cancelled)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchTicketWithComments(cfg, "PROJ-1")).thenReturn(ticketWithComments())

        service.runSummary(1L)

        verify(copilot, never()).execute(any<String>(), any<String>(), anyOrNull(), anyOrNull())

        assertEquals(AnalysisStatus.CANCELLED, cancelled.status)
    }

    @Test
    fun `runSummary bails out when the entity is cancelled between the originalText save and the ai call`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 7L)
        val cancelled2 =
            JiraTicketSummary(
                id = 1L,
                ticketKey = "PROJ-1",
                jiraConfigId = 7L,
                status = AnalysisStatus.CANCELLED,
            )
        var count = 0
        whenever(repo.findJiraTicketSummaryById(1L)).thenAnswer {
            count++
            if (count >= 2) cancelled2 else entity
        }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchTicketWithComments(cfg, "PROJ-1")).thenReturn(ticketWithComments())

        service.runSummary(1L)

        verify(copilot, never()).execute(any<String>(), any<String>(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `runSummary bails out when the entity is cancelled between the ai call and the save`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 7L)
        val cancelled3 =
            JiraTicketSummary(
                id = 1L,
                ticketKey = "PROJ-1",
                jiraConfigId = 7L,
                status = AnalysisStatus.CANCELLED,
            )
        var count3 = 0
        whenever(repo.findJiraTicketSummaryById(1L)).thenAnswer {
            count3++
            if (count3 >= 3) cancelled3 else entity
        }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchTicketWithComments(cfg, "PROJ-1")).thenReturn(ticketWithComments())
        whenever(copilot.execute(eq("gpt-4"), any<String>(), anyOrNull(), anyOrNull())).thenReturn("ok")

        service.runSummary(1L)

        assertEquals(AnalysisStatus.CANCELLED, cancelled3.status)
    }

    @Test
    fun `runSummary swallows EntityConcurrentlyModifiedException without writing FAILED state`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 7L)
        whenever(repo.findJiraTicketSummaryById(1L)).thenReturn(entity)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchTicketWithComments(cfg, "PROJ-1")).thenReturn(ticketWithComments())
        whenever(promptLoader.get(any<String>(), any<String>(), any())).thenReturn("TPL")

        whenever(repo.saveJiraTicketSummary(any())).thenThrow(EntityConcurrentlyModifiedException("x"))

        service.runSummary(1L)
    }

    @Test
    fun `runSummary marks FAILED on generic exception during the AI call`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 7L)
        whenever(repo.findJiraTicketSummaryById(1L)).thenReturn(entity)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchTicketWithComments(cfg, "PROJ-1")).thenReturn(ticketWithComments())
        whenever(promptLoader.get(any<String>(), any<String>(), any())).thenReturn("TPL")
        whenever(copilot.execute(eq("gpt-4"), any<String>(), anyOrNull(), anyOrNull())).thenThrow(RuntimeException("AI exploded"))
        whenever(i18n.translateError(any())).thenReturn("AI exploded (translated)")

        service.runSummary(1L)

        assertEquals(AnalysisStatus.FAILED, entity.status)
        assertEquals("AI exploded (translated)", entity.errorMessage)
    }

    @Test
    fun `runSummary swallows a second save failure when writing the FAILED state`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 7L)
        whenever(repo.findJiraTicketSummaryById(1L)).thenReturn(entity)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchTicketWithComments(cfg, "PROJ-1")).thenReturn(null)
        whenever(i18n.translateError(any())).thenReturn("nope")

        whenever(repo.saveJiraTicketSummary(any()))
            .thenAnswer { it.getArgument(0, JiraTicketSummary::class.java) }
            .thenThrow(RuntimeException("DB write failed"))

        service.runSummary(1L)
    }

    @Test
    fun `runSummary does NOT cancel when the row stays RUNNING between checks`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 7L)
        whenever(repo.findJiraTicketSummaryById(1L)).thenReturn(entity)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchTicketWithComments(cfg, "PROJ-1")).thenReturn(ticketWithComments())
        whenever(promptLoader.get(any<String>(), any<String>(), any())).thenReturn("TPL")
        whenever(copilot.execute(eq("gpt-4"), any<String>(), anyOrNull(), anyOrNull())).thenReturn("ok")

        service.runSummary(1L)

        assertEquals(AnalysisStatus.COMPLETED, entity.status)
        verify(copilot).execute(eq("gpt-4"), any<String>(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `runSummary treats a findById exception as not-cancelled`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 7L)
        var count = 0
        whenever(repo.findJiraTicketSummaryById(1L)).thenAnswer {
            count++

            if (count == 1) entity else throw RuntimeException("DB flaky")
        }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchTicketWithComments(cfg, "PROJ-1")).thenReturn(ticketWithComments())
        whenever(promptLoader.get(any<String>(), any<String>(), any())).thenReturn("TPL")
        whenever(copilot.execute(eq("gpt-4"), any<String>(), anyOrNull(), anyOrNull())).thenReturn("ok")

        service.runSummary(1L)

        assertEquals(AnalysisStatus.COMPLETED, entity.status)
    }

    @Test
    fun `buildOriginalText renders description and every comment in chronological order`() {
        val ticket =
            JiraTicketSummaryTicket(
                key = "PROJ-1",
                summary = "s",
                description = "desc",
                url = "https://j/browse/PROJ-1",
                status = "In Progress",
                issueType = "Story",
                priority = "High",
                assignee = "Alice",
                reporter = "Bob",
                labels = listOf("x", "y"),
                createdAt = "2025-11-04T09:00:00.000+0000",
                updatedAt = "2025-11-05T09:00:00.000+0000",
                comments =
                    listOf(
                        JiraTicketSummaryComment("Carol", "first comment", "2025-11-04T10:00:00.000+0000"),
                        JiraTicketSummaryComment("", "second", "2025-11-04T11:00:00.000+0000"),
                    ),
            )
        val out = service.buildOriginalText(ticket)
        assertTrue(out.contains("# PROJ-1 — s"))
        assertTrue(out.contains("Beschreibung"))
        assertTrue(out.contains("desc"))
        assertTrue(out.contains("Kommentare (2)"))
        assertTrue(out.contains("Carol"))
        assertTrue(out.contains("first comment"))
        assertTrue(out.contains("Unbekannt"))
        assertTrue(out.contains("second"))
    }

    @Test
    fun `buildOriginalText omits priority label when priority is blank`() {
        val ticket =
            JiraTicketSummaryTicket(
                key = "PROJ-1",
                summary = "s",
                description = "d",
                url = "u",
                status = "s",
                issueType = "Story",
                priority = "",
                assignee = "A",
                reporter = "B",
                labels = emptyList(),
                createdAt = "",
                updatedAt = "",
                comments = emptyList(),
            )
        val out = service.buildOriginalText(ticket)
        assertTrue(!out.contains("Priorität"))
    }

    @Test
    fun `buildOriginalText uses empty placeholder when description is blank`() {
        val ticket =
            JiraTicketSummaryTicket(
                key = "PROJ-1",
                summary = "s",
                description = "",
                url = "u",
                status = "s",
                issueType = "Story",
                priority = "p",
                assignee = "A",
                reporter = "B",
                labels = emptyList(),
                createdAt = "",
                updatedAt = "",
                comments = emptyList(),
            )
        val out = service.buildOriginalText(ticket)
        assertTrue(out.contains("(leer)"))
    }

    @Test
    fun `buildOriginalText omits assignee and reporter rows when blank`() {
        val ticket =
            JiraTicketSummaryTicket(
                key = "PROJ-1",
                summary = "s",
                description = "d",
                url = "u",
                status = "s",
                issueType = "Story",
                priority = "p",
                assignee = "",
                reporter = "",
                labels = emptyList(),
                createdAt = "",
                updatedAt = "",
                comments = emptyList(),
            )
        val out = service.buildOriginalText(ticket)
        assertTrue(!out.contains("Bearbeiter:"))
        assertTrue(!out.contains("Ersteller:"))
        assertTrue(!out.contains("Labels:"))
    }

    @Test
    fun `buildOriginalText omits the comments section when there are no comments`() {
        val ticket =
            JiraTicketSummaryTicket(
                key = "PROJ-1",
                summary = "s",
                description = "d",
                url = "u",
                status = "s",
                issueType = "Story",
                priority = "p",
                assignee = "A",
                reporter = "B",
                labels = emptyList(),
                createdAt = "",
                updatedAt = "",
                comments = emptyList(),
            )
        val out = service.buildOriginalText(ticket)
        assertTrue(!out.contains("## Kommentare"))
    }

    @Test
    fun `buildOriginalText does not emit a date for comments without a createdAt`() {
        val ticket =
            JiraTicketSummaryTicket(
                key = "PROJ-1",
                summary = "s",
                description = "d",
                url = "u",
                status = "s",
                issueType = "Story",
                priority = "p",
                assignee = "A",
                reporter = "B",
                labels = emptyList(),
                createdAt = "",
                updatedAt = "",
                comments = listOf(JiraTicketSummaryComment("Alice", "hi", "")),
            )
        val out = service.buildOriginalText(ticket)
        assertTrue(out.contains("1. Alice"))
        assertTrue(!out.contains("Alice —"))
    }

    @Test
    fun `resetStaleSummaries swallows individual entity save failure`() {
        val running = JiraTicketSummary(id = 1L, ticketKey = "RUN-1", status = AnalysisStatus.RUNNING)
        whenever(repo.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(running))

        whenever(repo.saveJiraTicketSummary(any())).thenThrow(RuntimeException("DB write failed for entity"))
        service.resetStaleSummaries()
    }

    @Test
    fun `startSummary uses explicit jiraConfigId when it exists`() {
        whenever(jiraConfigRepo.jiraConfigExistsById(7L)).thenReturn(true)
        val saved = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 7L)
        whenever(repo.saveJiraTicketSummary(any())).thenReturn(saved)
        val result = service.startSummary("PROJ-1", 7L, null)
        assertEquals(7L, result.jiraConfigId)
        verify(jiraConfigRepo, never()).findFirstByActiveTrue()
    }

    @Test
    fun `startSummary stores non-blank templateOverride on entity`() {
        val cfg = JiraConfig(id = 5L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        whenever(jiraConfigRepo.findFirstByActiveTrue()).thenReturn(cfg)
        whenever(jiraConfigRepo.jiraConfigExistsById(5L)).thenReturn(true)
        whenever(repo.saveJiraTicketSummary(any())).thenAnswer { it.getArgument(0, JiraTicketSummary::class.java) }
        service.startSummary("PROJ-1", null, "My Custom Template")
        val arg = org.mockito.kotlin.argumentCaptor<JiraTicketSummary>()
        verify(repo).saveJiraTicketSummary(arg.capture())
        assertEquals("My Custom Template", arg.firstValue.templateOverride)
    }

    @Test
    fun `runSummary continues when isCancelled throws (getOrDefault returns false)`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 7L)
        var callCount = 0
        whenever(repo.findJiraTicketSummaryById(1L)).thenAnswer {
            callCount++
            if (callCount == 2) throw RuntimeException("DB error in isCancelled")
            entity
        }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchTicketWithComments(cfg, "PROJ-1")).thenReturn(
            JiraTicketSummaryTicket(
                key = "PROJ-1",
                summary = "S",
                description = "D",
                url = "",
                status = "Open",
                issueType = "Story",
                priority = "",
                assignee = "",
                reporter = "",
                labels = emptyList(),
                createdAt = "",
                updatedAt = "",
                comments = emptyList(),
            ),
        )
        whenever(promptLoader.get(any<String>(), any<String>(), any())).thenReturn("TPL")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("Summary result")

        service.runSummary(1L)
    }

    @Test
    fun `runSummary builds prompt without optional fields when they are blank and uses English`() {
        whenever(copilot.resolveLanguage()).thenReturn("en")
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 7L)
        whenever(repo.findJiraTicketSummaryById(1L)).thenReturn(entity)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)

        val ticket =
            JiraTicketSummaryTicket(
                key = "PROJ-1",
                summary = "The Summary",
                description = "Description text",
                url = "",
                status = "Open",
                issueType = "Bug",
                priority = "",
                assignee = "",
                reporter = "",
                labels = emptyList(),
                createdAt = "",
                updatedAt = "",
                comments = emptyList(),
            )
        whenever(jira.fetchTicketWithComments(cfg, "PROJ-1")).thenReturn(ticket)
        whenever(promptLoader.get(any<String>(), any<String>(), any())).thenReturn("TEMPLATE")
        var capturedPrompt = ""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenAnswer {
            capturedPrompt = it.getArgument(1, String::class.java)
            "AI summary"
        }
        service.runSummary(1L)

        assertTrue(!capturedPrompt.contains("Priority:"))
        assertTrue(!capturedPrompt.contains("Assignee:"))
        assertTrue(!capturedPrompt.contains("Reporter:"))
        assertTrue(!capturedPrompt.contains("Labels:"))
        assertTrue(!capturedPrompt.contains("Created:"))
        assertTrue(!capturedPrompt.contains("Updated:"))
        assertTrue(!capturedPrompt.contains("URL:"))
        assertTrue(!capturedPrompt.contains("## COMMENTS"))

        assertTrue(!capturedPrompt.contains("IMPORTANT: Write your entire response in German"))
        assertEquals(AnalysisStatus.COMPLETED, entity.status)
    }

    @Test
    fun `runSummary includes comment section with date when comments have createdAt`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 7L)
        whenever(repo.findJiraTicketSummaryById(1L)).thenReturn(entity)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        val comment = JiraTicketSummaryComment(author = "Alice", body = "Hi", createdAt = "2025-01-01")
        val ticket =
            JiraTicketSummaryTicket(
                key = "PROJ-1",
                summary = "S",
                description = "D",
                url = "http://u",
                status = "Open",
                issueType = "Bug",
                priority = "High",
                assignee = "Bob",
                reporter = "Carol",
                labels = listOf("label1"),
                createdAt = "2025-01-01",
                updatedAt = "2025-01-02",
                comments = listOf(comment),
            )
        whenever(jira.fetchTicketWithComments(cfg, "PROJ-1")).thenReturn(ticket)
        whenever(promptLoader.get(any<String>(), any<String>(), any())).thenReturn("TPL")
        var capturedPrompt = ""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenAnswer {
            capturedPrompt = it.getArgument(1, String::class.java)
            "ok"
        }
        service.runSummary(1L)

        assertTrue(capturedPrompt.contains("Priority:"))
        assertTrue(capturedPrompt.contains("Assignee:"))
        assertTrue(capturedPrompt.contains("Reporter:"))
        assertTrue(capturedPrompt.contains("Labels:"))
        assertTrue(capturedPrompt.contains("Created:"))
        assertTrue(capturedPrompt.contains("Updated:"))
        assertTrue(capturedPrompt.contains("URL:"))
        assertTrue(capturedPrompt.contains("## COMMENTS"))
        assertTrue(capturedPrompt.contains("2025-01-01"))
        assertEquals(AnalysisStatus.COMPLETED, entity.status)
    }

    @Test
    fun `runSummary includes comment without date when comment has blank createdAt`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 7L)
        whenever(repo.findJiraTicketSummaryById(1L)).thenReturn(entity)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        val commentNoDate = JiraTicketSummaryComment(author = "Bob", body = "content", createdAt = "")
        val ticket =
            JiraTicketSummaryTicket(
                key = "PROJ-1",
                summary = "S",
                description = "D",
                url = "",
                status = "Open",
                issueType = "Bug",
                priority = "",
                assignee = "",
                reporter = "",
                labels = emptyList(),
                createdAt = "",
                updatedAt = "",
                comments = listOf(commentNoDate),
            )
        whenever(jira.fetchTicketWithComments(cfg, "PROJ-1")).thenReturn(ticket)
        whenever(promptLoader.get(any<String>(), any<String>(), any())).thenReturn("TPL")
        var capturedPrompt = ""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenAnswer {
            capturedPrompt = it.getArgument(1, String::class.java)
            "ok"
        }
        service.runSummary(1L)
        assertTrue(capturedPrompt.contains("## COMMENTS"))

        assertTrue(!capturedPrompt.contains("Bob ("))
    }

    @Test
    fun `runSummary returns early when third isCancelled (after AI call) returns true`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 7L)

        val cancelledEntity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", status = AnalysisStatus.CANCELLED)
        whenever(repo.findJiraTicketSummaryById(1L))
            .thenReturn(entity)
            .thenReturn(entity)
            .thenReturn(entity)
            .thenReturn(cancelledEntity)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        val ticket =
            JiraTicketSummaryTicket(
                key = "PROJ-1",
                summary = "S",
                description = "D",
                url = "",
                status = "Open",
                issueType = "Bug",
                priority = "",
                assignee = "",
                reporter = "",
                labels = emptyList(),
                createdAt = "",
                updatedAt = "",
                comments = emptyList(),
            )
        whenever(jira.fetchTicketWithComments(cfg, "PROJ-1")).thenReturn(ticket)
        whenever(promptLoader.get(any<String>(), any<String>(), any())).thenReturn("TPL")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("AI output")
        whenever(repo.saveJiraTicketSummary(any())).thenReturn(entity)

        service.runSummary(1L)

        assertNotEquals(AnalysisStatus.COMPLETED, entity.status)
    }

    @Test
    fun `runSummary handles EntityConcurrentlyModifiedException gracefully`() {
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 7L)
        whenever(repo.findJiraTicketSummaryById(1L)).thenReturn(entity)
        whenever(repo.saveJiraTicketSummary(any())).thenThrow(EntityConcurrentlyModifiedException("concurrent modification"))

        service.runSummary(1L)
    }

    @Test
    fun `runSummary with exception when fresh entity is null skips error save`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 7L)
        whenever(repo.findJiraTicketSummaryById(1L))
            .thenReturn(entity)
            .thenReturn(entity)
            .thenReturn(entity)
            .thenReturn(entity)
            .thenReturn(null)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        val ticket =
            JiraTicketSummaryTicket(
                key = "PROJ-1",
                summary = "S",
                description = "D",
                url = "",
                status = "Open",
                issueType = "Bug",
                priority = "",
                assignee = "",
                reporter = "",
                labels = emptyList(),
                createdAt = "",
                updatedAt = "",
                comments = emptyList(),
            )
        whenever(jira.fetchTicketWithComments(cfg, "PROJ-1")).thenReturn(ticket)
        whenever(promptLoader.get(any<String>(), any<String>(), any())).thenReturn("TPL")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenThrow(RuntimeException("AI failure"))
        whenever(repo.saveJiraTicketSummary(any())).thenReturn(entity)

        service.runSummary(1L)
    }

    @Test
    fun `isCancelled returns false when findJiraTicketSummaryById returns null`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 7L)

        whenever(repo.findJiraTicketSummaryById(1L))
            .thenReturn(entity)
            .thenReturn(null)
            .thenReturn(null)
            .thenReturn(null)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        val ticket =
            JiraTicketSummaryTicket(
                key = "PROJ-1",
                summary = "S",
                description = "D",
                url = "",
                status = "Open",
                issueType = "Bug",
                priority = "",
                assignee = "",
                reporter = "",
                labels = emptyList(),
                createdAt = "",
                updatedAt = "",
                comments = emptyList(),
            )
        whenever(jira.fetchTicketWithComments(cfg, "PROJ-1")).thenReturn(ticket)
        whenever(promptLoader.get(any<String>(), any<String>(), any())).thenReturn("TPL")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("result")
        whenever(repo.saveJiraTicketSummary(any())).thenReturn(entity)

        service.runSummary(1L)
        assertEquals(AnalysisStatus.COMPLETED, entity.status)
    }

    @Test
    fun `cancelSummary swallows exception when save throws`() {
        val entity = JiraTicketSummary(id = 1L, jiraConfigId = 7L, ticketKey = "PROJ-1", status = AnalysisStatus.RUNNING)
        whenever(repo.findJiraTicketSummaryById(1L)).thenReturn(entity)
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(i18n.tFor(any<String>(), any<String>())).thenReturn("cancelled")
        whenever(repo.saveJiraTicketSummary(any())).thenThrow(RuntimeException("DB down"))

        service.cancelSummary(1L)
        assertEquals(AnalysisStatus.CANCELLED, entity.status)
    }

    @Test
    fun `runSummary uses empty fallback when description is blank`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 7L)
        whenever(repo.findJiraTicketSummaryById(1L)).thenReturn(entity)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        val ticket = ticketWithComments(description = "", comments = listOf(JiraTicketSummaryComment("", "body", "")))
        whenever(jira.fetchTicketWithComments(cfg, "PROJ-1")).thenReturn(ticket)
        whenever(promptLoader.get(any<String>(), eq("ticketsummary.summarize_ticket"), any())).thenReturn("TEMPLATE")
        whenever(copilot.execute(eq("gpt-4"), any<String>(), anyOrNull(), anyOrNull())).thenReturn("result")

        service.runSummary(1L)
        assertEquals(AnalysisStatus.COMPLETED, entity.status)
        assertNotNull(entity.originalText)
    }

    @Test
    fun `runSummary with null jiraConfigId marks FAILED`() {
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = null)
        whenever(repo.findJiraTicketSummaryById(1L)).thenReturn(entity)

        service.runSummary(1L)
        assertEquals(AnalysisStatus.FAILED, entity.status)
    }

    @Test
    fun `runSummary error path when findById returns null skips FAILED save`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 7L)
        whenever(repo.findJiraTicketSummaryById(1L))
            .thenReturn(entity)
            .thenReturn(null)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchTicketWithComments(cfg, "PROJ-1")).thenThrow(RuntimeException("fetch failed"))

        service.runSummary(1L)
    }

    @Test
    fun `runSummary error path when findById throws during re-fetch yields null`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = JiraTicketSummary(id = 1L, ticketKey = "PROJ-1", jiraConfigId = 7L)
        whenever(repo.findJiraTicketSummaryById(1L))
            .thenReturn(entity)
            .thenThrow(RuntimeException("DB connection lost"))
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchTicketWithComments(cfg, "PROJ-1")).thenThrow(RuntimeException("fetch failed"))

        service.runSummary(1L)
    }
}
