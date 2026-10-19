package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class JiraTicketSummaryCoverageTest {
    @Test
    fun `JiraTicketSummary defaults are sensible`() {
        val s = JiraTicketSummary(ticketKey = "PROJ-1")
        assertEquals(0L, s.id)
        assertEquals("PROJ-1", s.ticketKey)
        assertNull(s.jiraConfigId)
        assertNull(s.templateOverride)
        assertEquals("PENDING", s.status)
        assertNull(s.currentStep)
        assertNull(s.errorMessage)
        assertNull(s.originalText)
        assertNull(s.summaryMarkdown)
        assertNull(s.summaryHtml)
        assertNotNull(s.createdAt)
        assertNull(s.completedAt)
    }

    @Test
    fun `JiraTicketSummary stores provided values verbatim`() {
        val now = LocalDateTime.of(2025, 1, 1, 0, 0)
        val done = LocalDateTime.of(2025, 1, 1, 1, 0)
        val s =
            JiraTicketSummary(
                id = 7L,
                ticketKey = "PROJ-7",
                jiraConfigId = 3L,
                templateOverride = "tmpl",
                status = "COMPLETED",
                currentStep = "step",
                errorMessage = "err",
                originalText = "raw",
                summaryMarkdown = "# Title",
                summaryHtml = "<h1>Title</h1>",
                createdAt = now,
                completedAt = done,
            )
        assertEquals(7L, s.id)
        assertEquals(3L, s.jiraConfigId)
        assertEquals("tmpl", s.templateOverride)
        assertEquals("COMPLETED", s.status)
        assertEquals("step", s.currentStep)
        assertEquals("err", s.errorMessage)
        assertEquals("raw", s.originalText)
        assertEquals("# Title", s.summaryMarkdown)
        assertEquals("<h1>Title</h1>", s.summaryHtml)
        assertEquals(now, s.createdAt)
        assertEquals(done, s.completedAt)
    }
}
