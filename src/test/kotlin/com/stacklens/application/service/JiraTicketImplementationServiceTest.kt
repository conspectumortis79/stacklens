package com.stacklens.application.service

import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.application.port.out.JiraTicketImplementationPort
import com.stacklens.application.port.out.jira.JiraTicketFetchPort
import com.stacklens.application.port.out.transaction.TransactionPort
import com.stacklens.application.service.I18nService
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.JiraTicketImplementation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class JiraTicketImplementationServiceTest {
    private val analysisRepo = mock<JiraTicketImplementationPort>()
    private val jiraConfigRepo = mock<JiraConfigPort>()
    private val jiraHttp = mock<JiraTicketFetchPort>()
    private val i18n = mock<I18nService>(defaultAnswer = org.mockito.stubbing.Answer { _ -> "" })

    private val service =
        JiraTicketImplementationService(
            analysisRepo,
            jiraConfigRepo,
            jiraHttp,
            i18n,
            object : TransactionPort {
                override fun <T> inTransaction(block: () -> T): T = block()

                override fun afterCommit(block: () -> Unit) = block()
            },
        )

    private fun jiraConfig(id: Long = 1L) =
        JiraConfig(
            id = id,
            name = "Test",
            baseUrl = "https://jira.example.com",
            username = "user",
            apiToken = "token",
            projectKey = "PROJ",
        )

    private fun ticketData(
        key: String = "PROJ-1",
        summary: String = "Fix the bug",
        description: String = "Details here",
        issueType: String = "Bug",
        status: String = "Open",
    ) = JiraTicketData(
        key = key,
        summary = summary,
        description = description,
        issueType = issueType,
        status = status,
        priority = null,
        components = emptyList(),
        labels = emptyList(),
        acceptanceCriteria = emptyList(),
        comments = "",
        url = "https://jira.example.com/browse/$key",
    )

    private fun analysis(
        id: Long,
        status: String,
    ) = JiraTicketImplementation(id = id, ticketKey = "PROJ-1", status = status, createdAt = LocalDateTime.now())

    @Test
    fun findAnalysisByIdDelegatesToRepo() {
        val a = analysis(5L, "COMPLETED")
        whenever(analysisRepo.findJiraTicketImplementationById(5L)).thenReturn(a)
        assertEquals(a, service.findAnalysisById(5L))
    }

    @Test
    fun findAnalysisByIdReturnsNullWhenNotFound() {
        whenever(analysisRepo.findJiraTicketImplementationById(99L)).thenReturn(null)
        assertEquals(null, service.findAnalysisById(99L))
    }

    @Test
    fun fetchTicketAsTextThrowsWhenKeyIsBlank() {
        assertThrows<IllegalArgumentException> {
            service.fetchTicketAsText("   ", 1L)
        }
    }

    @Test
    fun fetchTicketAsTextThrowsWhenKeyInvalidFormat() {
        assertThrows<IllegalArgumentException> {
            service.fetchTicketAsText("not-valid", 1L)
        }
    }

    @Test
    fun fetchTicketAsTextThrowsWhenConfigNotFound() {
        whenever(jiraConfigRepo.findJiraConfigById(99L)).thenReturn(null)
        assertThrows<IllegalArgumentException> {
            service.fetchTicketAsText("PROJ-1", 99L)
        }
    }

    @Test
    fun fetchTicketAsTextReturnsFormattedText() {
        val cfg = jiraConfig(1L)
        whenever(jiraConfigRepo.findJiraConfigById(1L)).thenReturn(cfg)
        whenever(jiraHttp.fetchTicketByKey(eq(cfg), eq("PROJ-1"))).thenReturn(ticketData())

        val text = service.fetchTicketAsText("PROJ-1", 1L)

        assertTrue(text.contains("Ticket: PROJ-1"))
        assertTrue(text.contains("Type: Bug"))
        assertTrue(text.contains("Status: Open"))
        assertTrue(text.contains("Summary: Fix the bug"))
        assertTrue(text.contains("Details here"))
    }

    @Test
    fun fetchTicketAsTextNormalizesTicketKeyToUpperCase() {
        val cfg = jiraConfig(1L)
        whenever(jiraConfigRepo.findJiraConfigById(1L)).thenReturn(cfg)
        whenever(jiraHttp.fetchTicketByKey(eq(cfg), eq("PROJ-42"))).thenReturn(ticketData("PROJ-42"))

        service.fetchTicketAsText("proj-42", 1L)

        verify(jiraHttp).fetchTicketByKey(eq(cfg), eq("PROJ-42"))
    }

    @Test
    fun getAllAnalysesReturnsList() {
        val list = listOf(analysis(1L, "COMPLETED"), analysis(2L, "FAILED"))
        whenever(analysisRepo.findRecentJiraTicketImplementations(200)).thenReturn(list)

        val result = service.getAllAnalyses()

        assertEquals(2, result.size)
    }

    @Test
    fun deleteAnalysisDelegatesById() {
        service.deleteAnalysis(42L)
        verify(analysisRepo).deleteJiraTicketImplementationById(42L)
    }

    @Test
    fun deleteAllFinishedSkipsRunningAnalysis() {
        whenever(analysisRepo.deleteFinishedAnalyses()).thenReturn(1)
        service.deleteAllFinishedAnalyses()
        verify(analysisRepo).deleteFinishedAnalyses()
    }

    @Test
    fun deleteAllFinishedSkipsPendingAnalysis() {
        whenever(analysisRepo.deleteFinishedAnalyses()).thenReturn(1)
        service.deleteAllFinishedAnalyses()
        verify(analysisRepo).deleteFinishedAnalyses()
    }

    @Test
    fun deleteAllFinishedSkipsAnalysisWithZeroId() {
        val zeroIdAnalysis =
            JiraTicketImplementation(id = 0L, ticketKey = "PROJ-X", status = "COMPLETED", createdAt = java.time.LocalDateTime.now())
        whenever(analysisRepo.findRecentJiraTicketImplementations(200)).thenReturn(listOf(zeroIdAnalysis))
        service.deleteAllFinishedAnalyses()
        verify(analysisRepo, never()).deleteJiraTicketImplementationById(0L)
    }

    @Test
    fun fetchTicketAsTextIncludesPriorityWhenNonBlank() {
        val cfg = jiraConfig(1L)
        whenever(jiraConfigRepo.findJiraConfigById(1L)).thenReturn(cfg)
        val ticket = ticketData().copy(priority = "High")
        whenever(jiraHttp.fetchTicketByKey(eq(cfg), eq("PROJ-1"))).thenReturn(ticket)

        val text = service.fetchTicketAsText("PROJ-1", 1L)

        assertTrue(text.contains("Priority: High"))
    }

    @Test
    fun fetchTicketAsTextOmitsPriorityWhenBlank() {
        val cfg = jiraConfig(1L)
        whenever(jiraConfigRepo.findJiraConfigById(1L)).thenReturn(cfg)
        val ticket = ticketData().copy(priority = "")
        whenever(jiraHttp.fetchTicketByKey(eq(cfg), eq("PROJ-1"))).thenReturn(ticket)

        val text = service.fetchTicketAsText("PROJ-1", 1L)

        assertTrue(!text.contains("Priority:"))
    }

    @Test
    fun fetchTicketAsTextIncludesComponentsAndLabelsWhenPresent() {
        val cfg = jiraConfig(1L)
        whenever(jiraConfigRepo.findJiraConfigById(1L)).thenReturn(cfg)
        val ticket = ticketData().copy(components = listOf("auth", "api"), labels = listOf("backend"))
        whenever(jiraHttp.fetchTicketByKey(eq(cfg), eq("PROJ-1"))).thenReturn(ticket)

        val text = service.fetchTicketAsText("PROJ-1", 1L)

        assertTrue(text.contains("Components: auth, api"))
        assertTrue(text.contains("Labels: backend"))
    }

    @Test
    fun fetchTicketAsTextOmitsDescriptionSectionWhenBlank() {
        val cfg = jiraConfig(1L)
        whenever(jiraConfigRepo.findJiraConfigById(1L)).thenReturn(cfg)
        val ticket = ticketData().copy(description = "")
        whenever(jiraHttp.fetchTicketByKey(eq(cfg), eq("PROJ-1"))).thenReturn(ticket)

        val text = service.fetchTicketAsText("PROJ-1", 1L)

        assertTrue(!text.contains("## Description"))
    }

    @Test
    fun fetchTicketAsTextIncludesAcceptanceCriteriaWhenPresent() {
        val cfg = jiraConfig(1L)
        whenever(jiraConfigRepo.findJiraConfigById(1L)).thenReturn(cfg)
        val ticket = ticketData().copy(acceptanceCriteria = listOf("AC1", "AC2"))
        whenever(jiraHttp.fetchTicketByKey(eq(cfg), eq("PROJ-1"))).thenReturn(ticket)

        val text = service.fetchTicketAsText("PROJ-1", 1L)

        assertTrue(text.contains("## Acceptance Criteria"))
        assertTrue(text.contains("- AC1"))
        assertTrue(text.contains("- AC2"))
    }

    @Test
    fun fetchTicketAsTextIncludesCommentsWhenPresent() {
        val cfg = jiraConfig(1L)
        whenever(jiraConfigRepo.findJiraConfigById(1L)).thenReturn(cfg)
        val ticket = ticketData().copy(comments = "User commented here.")
        whenever(jiraHttp.fetchTicketByKey(eq(cfg), eq("PROJ-1"))).thenReturn(ticket)

        val text = service.fetchTicketAsText("PROJ-1", 1L)

        assertTrue(text.contains("## Comments"))
        assertTrue(text.contains("User commented here."))
    }

    @Test
    fun fetchTicketAsTextTruncatesCommentsAt20000Chars() {
        val cfg = jiraConfig(1L)
        whenever(jiraConfigRepo.findJiraConfigById(1L)).thenReturn(cfg)
        val longComments = "x".repeat(25000)
        val ticket = ticketData().copy(comments = longComments)
        whenever(jiraHttp.fetchTicketByKey(eq(cfg), eq("PROJ-1"))).thenReturn(ticket)

        val text = service.fetchTicketAsText("PROJ-1", 1L)

        assertTrue(text.contains("x".repeat(20000)))
        assertTrue(!text.contains("x".repeat(20001)))
    }
}
