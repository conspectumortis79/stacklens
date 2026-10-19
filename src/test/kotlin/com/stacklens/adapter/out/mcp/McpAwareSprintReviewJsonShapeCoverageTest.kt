package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.SprintReviewJiraPort
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class McpAwareSprintReviewJsonShapeCoverageTest {
    private val mcpRepo: McpServerConfigPort = mock()
    private val gateway: McpGatewayPort = mock()
    private val delegate: SprintReviewJiraPort = mock()
    private val adapter = McpAwareSprintReviewJiraAdapter(mcpRepo, gateway, ObjectMapper(), delegate)

    private val config = JiraConfig(id = 7L, name = "banana", baseUrl = "https://jira.test/", apiToken = "tok", cloudMode = true)

    init {
        whenever(mcpRepo.findFirstActiveMcpServerConfig())
            .thenReturn(
                McpServerConfig(
                    id = 1L,
                    name = "mcp",
                    baseUrl = "http://localhost:7456",
                    active = true,
                    routeJira = true,
                    jiraBaseUrl = "https://jira.test",
                    jiraApiToken = "tok",
                ),
            )
    }

    private fun respondWith(payload: String) {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(text = payload, isError = false))
    }

    @Test
    fun `blank labels are dropped and null custom fields are ignored`() {
        respondWith(
            """
            {"total":1,"issues":[
              {"key":"P-1","fields":{
                "summary":"S","description":"D","labels":["","  ","Frontend"],
                "customfield_points":null,
                "customfield_empty":{},
                "customfield_value":{"value":"3,5"},
                "customfield_text":{"value":""},
                "customfield_num":{"other":7},
                "customfield_number":12
              }}
            ]}
            """.trimIndent(),
        )

        val tickets = adapter.fetchSprintTickets(config, 42L)

        val ticket = tickets.single()
        assertEquals(listOf("Frontend"), ticket.labels)
        assertEquals(
            listOf("customfield_number: 12.0", "customfield_value: 3.5"),
            ticket.numericCustomFields.sorted(),
        )
        assertTrue(ticket.storyPoints == null || ticket.storyPoints == 3.5)
    }
}
