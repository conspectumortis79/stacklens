package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JiraTicketSummaryTest {
    @Test
    fun `default constructor populates sensible defaults`() {
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
        assertNull(s.completedAt)
    }

    @Test
    fun `named constructor populates all fields`() {
        val s =
            JiraTicketSummary(
                id = 1L,
                ticketKey = "PROJ-1",
                jiraConfigId = 2L,
                templateOverride = "t",
                status = "COMPLETED",
                currentStep = "step",
                errorMessage = "err",
                originalText = "orig",
                summaryMarkdown = "md",
                summaryHtml = "<p>html</p>",
            )
        assertEquals(1L, s.id)
        assertEquals(2L, s.jiraConfigId)
        assertEquals("t", s.templateOverride)
        assertEquals("COMPLETED", s.status)
        assertEquals("step", s.currentStep)
        assertEquals("err", s.errorMessage)
        assertEquals("orig", s.originalText)
        assertEquals("md", s.summaryMarkdown)
        assertEquals("<p>html</p>", s.summaryHtml)
    }

    @Test
    fun `setter methods update fields via reflection (JPA simulation)`() {
        val s = JiraTicketSummary(ticketKey = "INITIAL")
        for (
        (name, expectedValue) in
        listOf(
            "ticketKey" to "PROJ-99",
            "jiraConfigId" to 42L,
            "templateOverride" to "custom-template",
        )
        ) {
            val setter =
                JiraTicketSummary::class.java.getDeclaredMethod(
                    "set" + name.replaceFirstChar { it.uppercase() },
                    expectedValue::class.java,
                )
            setter.isAccessible = true
            setter.invoke(s, expectedValue)
        }
        assertEquals("PROJ-99", s.ticketKey)
        assertEquals(42L, s.jiraConfigId)
        assertEquals("custom-template", s.templateOverride)
    }
}
