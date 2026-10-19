package com.stacklens.application.service

import com.stacklens.adapter.out.demo.DemoTicketSimulator
import com.stacklens.application.port.model.SubtaskDraft
import com.stacklens.application.port.out.TicketCreationJiraPort
import com.stacklens.application.port.out.ticket.TicketCreationDemoPort
import com.stacklens.domain.model.CreatedJiraTicket
import com.stacklens.domain.model.JiraConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class JiraSubtaskCreatorDemoModeTest {
    private val jiraPort: TicketCreationJiraPort = mock()
    private val creator = JiraSubtaskCreator(jiraPort, DemoTicketSimulator(mock()))

    private fun config(name: String) =
        JiraConfig(
            id = 1L,
            name = name,
            baseUrl = "http://localhost:8081",
            projectKey = "SPRINT",
            username = "demo",
            apiToken = "token",
        )

    private val drafts =
        listOf(
            SubtaskDraft(summary = "  Audit & Logging  ", description = "Wer, wann, wo"),
            SubtaskDraft(summary = "   ", description = "Read-only Endpoint"),
        )

    @Test
    fun `a demo config simulates subtasks without touching the jira write api`() {
        val result = creator.createAll(config("Demo Jira"), "SPRINT-100", "SPRINT", drafts)

        verify(jiraPort, never()).createSubtask(any(), any(), any(), any(), any(), any(), anyOrNull(), anyOrNull())
        assertEquals(2, result.size)
        assertEquals("Audit & Logging", result[0]["summary"])
        assertEquals("SPRINT-9001", result[0]["key"])
        assertEquals("SPRINT-9002", result[1]["key"])
        assertEquals("Subtask", result[1]["issueType"])
        assertNull(result[0]["error"])
        assertEquals("Subtask 2", result[1]["summary"])
    }

    @Test
    fun `an injected simulation port decides demo mode without matching the config name`() {
        val simulation = mock<TicketCreationDemoPort>()
        whenever(simulation.isDemo(any())).thenReturn(true)
        whenever(simulation.simulatedSubtasks(any(), any())).thenReturn(listOf(mapOf("key" to "X-1")))
        val injected = JiraSubtaskCreator(jiraPort, demoSimulation = simulation)

        val result = injected.createAll(config("Prod Jira"), "SPRINT-100", "SPRINT", drafts.take(1))

        verify(jiraPort, never()).createSubtask(any(), any(), any(), any(), any(), any(), anyOrNull(), anyOrNull())
        assertEquals("X-1", result.single()["key"])
    }

    @Test
    fun `a non demo config still creates through the jira port`() {
        whenever(jiraPort.createSubtask(any(), any(), any(), any(), any(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(CreatedJiraTicket(key = "SPRINT-300", id = "300", url = "/browse/SPRINT-300"))

        val result = creator.createAll(config("Prod Jira"), "SPRINT-100", "SPRINT", drafts.take(1))

        verify(jiraPort).createSubtask(any(), any(), any(), any(), any(), any(), anyOrNull(), anyOrNull())
        assertEquals("SPRINT-300", result[0]["key"])
    }
}
