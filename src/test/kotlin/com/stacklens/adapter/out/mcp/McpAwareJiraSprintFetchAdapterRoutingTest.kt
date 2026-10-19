package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.jira.JiraSprintFetchPort
import com.stacklens.domain.model.JiraConfig
import com.stacklens.shared.sprintIdFromRegex
import com.stacklens.testsupport.McpRoutingFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class McpAwareJiraSprintFetchAdapterRoutingTest {
    private val objectMapper = ObjectMapper()
    private val gateway: McpGatewayPort = mock()
    private val delegate: JiraSprintFetchPort = mock()
    private val adapter =
        McpAwareJiraSprintFetchAdapter(
            mcpRepo = McpRoutingFixtures.repoWith(McpRoutingFixtures.routed()),
            gateway = gateway,
            objectMapper = objectMapper,
            delegate = delegate,
        )

    private fun jiraConfig() =
        JiraConfig(
            name = "j",
            baseUrl = McpRoutingFixtures.JIRA_BASE_URL,
            username = "u",
            apiToken = "t",
            active = true,
        )

    private fun answer(json: String) = whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(text = json))

    private fun page(
        size: Int,
        total: Int,
        from: Int = 1,
    ): String {
        val issues =
            (from..from + size - 1).joinToString(",") { index ->
                """{"key":"K-$index","fields":{"summary":"issue $index","resolutiondate":"2024-05-0$((index % 9) + 1)T09:00:00.000+0200"}}"""
            }
        return """{"total":$total,"issues":[$issues]}"""
    }

    @Test
    fun `a regex without a capture group yields no id`() {
        assertNull(sprintIdFromRegex(Regex("board"), "https://jira.example.com/boards/12"))
        assertEquals(12L, sprintIdFromRegex(Regex("""/boards?/(\d+)"""), "https://jira.example.com/boards/12"))
        assertNull(sprintIdFromRegex(Regex("""/boards?/(\d+)"""), "https://jira.example.com/nothing"))
        assertNull(sprintIdFromRegex(Regex("""/boards?/(\d+)"""), "https://jira.example.com/boards/not-a-number"))
    }

    @Test
    fun `a board url resolves the sprint through the active sprint call`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = """{"values":[{"id":99,"name":"Sprint 99"}]}"""),
            McpToolResult(text = page(1, 1)),
        )

        val issues = adapter.fetchSprintIssues(jiraConfig(), "https://jira.example.com/secure/Board.jspa?rapidView=1&selectedIssue=K-1/boards/77")

        assertEquals(1, issues.size)
        assertTrue(issues.single().resolvedAt!!.isNotBlank())
    }

    @Test
    fun `an active sprint answer without an array falls back to the delegate`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = """{"values":{"id":1}}"""),
        )
        whenever(delegate.fetchSprintIssues(any(), any())).thenReturn(emptyList())

        assertEquals(emptyList<Any>(), adapter.fetchSprintIssues(jiraConfig(), "https://jira.example.com/boards/77"))
    }

    @Test
    fun `pagination continues while the page is full and the total is ahead`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = page(100, 500)),
            McpToolResult(text = page(100, 500, from = 101)),
            McpToolResult(text = page(20, 500, from = 201)),
        )

        val issues = adapter.fetchSprintIssues(jiraConfig(), "https://jira.example.com/sprints/5")

        assertEquals(220, issues.size)
    }

    @Test
    fun `an issue page that is not an array stops the collection`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = """{"issues":{"key":"K-1"},"total":10}"""),
        )

        assertEquals(0, adapter.fetchSprintIssues(jiraConfig(), "https://jira.example.com/sprints/5").size)
    }

    @Test
    fun `an issue without a resolution date stays unresolved`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = """{"total":1,"issues":[{"key":"K-9","fields":{"summary":"open","resolutiondate":""}}]}"""),
        )

        val issues = adapter.fetchSprintIssues(jiraConfig(), "https://jira.example.com/sprints/5")

        assertEquals(1, issues.size)
        assertNull(issues.single().resolvedAt)
    }

    @Test
    fun `an error result from the gateway falls back to the delegate`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(text = "boom", isError = true))
        whenever(delegate.fetchSprintIssues(any(), any())).thenReturn(listOf())

        assertEquals(0, adapter.fetchSprintIssues(jiraConfig(), "https://jira.example.com/sprints/5").size)
    }

    @Test
    fun `a mismatching jira base url uses the delegate directly`() {
        val other =
            McpAwareJiraSprintFetchAdapter(
                mcpRepo = McpRoutingFixtures.repoWith(McpRoutingFixtures.routed(jiraBaseUrl = "https://other.example.com")),
                gateway = gateway,
                objectMapper = objectMapper,
                delegate = delegate,
            )
        whenever(delegate.fetchSprintIssues(any(), any())).thenReturn(listOf())

        assertEquals(0, other.fetchSprintIssues(jiraConfig(), "https://jira.example.com/sprints/5").size)
    }
}
