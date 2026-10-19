package com.stacklens.application.port.out

import com.stacklens.domain.model.CreatedJiraTicket
import com.stacklens.domain.model.JiraConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TicketCreationJiraPortTest {
    private val port =
        object : TicketCreationJiraPort {
            var lastPriority: String? = null
            var lastIssueType: String? = null
            var lastLabels: List<String>? = null

            override fun createTicket(
                config: JiraConfig,
                projectKey: String,
                summary: String,
                description: String,
                issueType: String,
                labels: List<String>,
                priority: String,
                customFields: Map<String, Any>,
            ): CreatedJiraTicket {
                lastPriority = priority
                lastIssueType = issueType
                lastLabels = labels
                return CreatedJiraTicket(key = "KEY-1", id = "100", url = "https://x")
            }

            override fun requiredCreateFields(
                config: JiraConfig,
                projectKey: String,
                issueType: String,
            ): List<com.stacklens.domain.model.JiraCreateField> = emptyList()

            override fun createSubtask(
                config: JiraConfig,
                parentKey: String,
                projectKey: String,
                summary: String,
                description: String,
                issueType: String,
                labels: List<String>,
                priority: String,
            ): CreatedJiraTicket {
                lastPriority = priority
                lastIssueType = issueType
                lastLabels = labels
                return CreatedJiraTicket(key = "KEY-1", id = "100", url = "https://x")
            }
        }

    @Test
    fun `createTicket uses default priority when not provided`() {
        val cfg = JiraConfig(name = "j", baseUrl = "https://j", apiToken = "t")
        val ticket =
            port.createTicket(
                config = cfg,
                projectKey = "PROJ",
                summary = "s",
                description = "d",
                issueType = "Story",
                labels = listOf("a"),
            )
        assertEquals("KEY-1", ticket.key)
        assertEquals("Minor", port.lastPriority)
    }

    @Test
    fun `createTicket forwards explicit priority when provided`() {
        val cfg = JiraConfig(name = "j", baseUrl = "https://j", apiToken = "t")
        port.createTicket(
            config = cfg,
            projectKey = "PROJ",
            summary = "s",
            description = "d",
            issueType = "Story",
            labels = listOf("a"),
            priority = "High",
        )
        assertEquals("High", port.lastPriority)
    }

    @Test
    fun `createSubtask uses default values when not provided`() {
        val cfg = JiraConfig(name = "j", baseUrl = "https://j", apiToken = "t")
        val ticket =
            port.createSubtask(
                config = cfg,
                parentKey = "PARENT-1",
                projectKey = "PROJ",
                summary = "s",
                description = "d",
            )
        assertEquals("KEY-1", ticket.key)
        assertEquals("Subtask", port.lastIssueType)
        assertEquals(emptyList<String>(), port.lastLabels)
        assertEquals("Minor", port.lastPriority)
    }

    @Test
    fun `createSubtask forwards explicit values when provided`() {
        val cfg = JiraConfig(name = "j", baseUrl = "https://j", apiToken = "t")
        port.createSubtask(
            config = cfg,
            parentKey = "PARENT-1",
            projectKey = "PROJ",
            summary = "s",
            description = "d",
            issueType = "Bug",
            labels = listOf("x"),
            priority = "Critical",
        )
        assertEquals("Bug", port.lastIssueType)
        assertEquals(listOf("x"), port.lastLabels)
        assertEquals("Critical", port.lastPriority)
    }
}
