package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.JiraTicketSummaryJiraPort
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpProcessControlPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.SprintReviewTicket
import com.stacklens.application.port.out.TicketRefinementJiraPort
import com.stacklens.application.port.out.database.DatabaseScanPort
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.javaMethod

class McpAwareAdaptersResidualCoverageTest {
    private val mcpRepo = mock<McpServerConfigPort>()

    private val gateway = mock<McpGatewayPort>()

    private val mapper = ObjectMapper()

    private val jiraConfig =
        JiraConfig(
            name = "c",
            baseUrl = "https://jira.example.com",
            apiToken = "t",
            projectKey = "PROJ",
            username = "u",
        )

    private val routingConfig =
        McpServerConfig(
            active = true,
            routeJira = true,
            routeDatabase = true,
            routeGitlab = true,
            jiraBaseUrl = "https://jira.example.com",
            jiraApiToken = "t",
            gitlabBaseUrl = "https://gitlab.example.com",
            gitlabAccessToken = "glpat",
        )

    private val parentJson =
        """
        {"key":"P-1","fields":{"summary":"parent","description":"body",
         "issuelinks":[{"inwardIssue":{"key":"L-1"}},{"outwardIssue":{"key":"L-2"}},{"inwardIssue":{"key":""}}],
         "subtasks":[{"key":"S-1"},{"key":""}]}}
        """.trimIndent()

    private val plainJson = """{"key":"%s","fields":{"summary":"child"}}"""

    @BeforeEach
    fun routeMcp() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(routingConfig)
    }

    private fun stubTool(vararg results: McpToolResult) {
        val iterator = results.iterator()
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            if (iterator.hasNext()) iterator.next() else McpToolResult("""{"key":"x","fields":{"summary":"s"}}""")
        }
    }

    @Test
    fun `ticket refinement collects linked and sub task children of the parent`() {
        val delegate = mock<TicketRefinementJiraPort>()
        val adapter = McpAwareTicketRefinementJiraAdapter(mcpRepo, gateway, mapper, delegate)
        stubTool(
            McpToolResult(parentJson),
            McpToolResult(parentJson),
            McpToolResult(plainJson.format("L-1")),
            McpToolResult(plainJson.format("L-2")),
            McpToolResult(plainJson.format("S-1")),
        )

        val tickets = adapter.fetchParentWithLinkedTickets(jiraConfig, "P-1")

        assertEquals(listOf("P-1", "L-1", "L-2", "S-1"), tickets.map { it.key })
    }

    @Test
    fun `ticket refinement keeps only the parent when the link lookup reports an error`() {
        val delegate = mock<TicketRefinementJiraPort>()
        val adapter = McpAwareTicketRefinementJiraAdapter(mcpRepo, gateway, mapper, delegate)
        stubTool(
            McpToolResult(parentJson),
            McpToolResult("boom", isError = true),
        )

        val tickets = adapter.fetchParentWithLinkedTickets(jiraConfig, "P-1")

        assertEquals(listOf("P-1"), tickets.map { it.key })
    }

    @Test
    fun `ticket refinement keeps only the parent when the link lookup throws`() {
        val delegate = mock<TicketRefinementJiraPort>()
        val adapter = McpAwareTicketRefinementJiraAdapter(mcpRepo, gateway, mapper, delegate)
        var calls = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            calls++
            if (calls == 1) McpToolResult(parentJson) else throw RuntimeException("link lookup failed")
        }

        val tickets = adapter.fetchParentWithLinkedTickets(jiraConfig, "P-1")

        assertEquals(listOf("P-1"), tickets.map { it.key })
    }

    @Test
    fun `database scan maps every known hit kind and drops unknown ones`() {
        val delegate = mock<DatabaseScanPort>()
        val adapter = McpAwareDatabaseScanAdapter(mcpRepo, gateway, mapper, delegate)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                """{"databaseName":"mydb","hits":[
                   {"kind":"table","tableName":"","columnName":"email"},
                   {"kind":"column","tableName":"users","columnName":"email"},
                   {"kind":"view","tableName":"user_view"},
                   {"kind":"index","tableName":"idx"}
                ]}""",
            ),
        )
        val config =
            DatabaseConfig(
                name = "db",
                dbType = DatabaseType.POSTGRESQL,
                host = "h",
                port = 5432,
                databaseName = "mydb",
                username = "u",
                password = "p",
            )

        val results = adapter.scanDatabase(config, "email", 1L).results

        assertEquals(3, results.size)
        assertEquals(ResultType.TABLE, results[0].resultType)
        assertNull(results[0].tableName)
        assertEquals("users", results[1].tableName)
        assertEquals(ResultType.VIEW, results[2].resultType)
    }

    @Test
    fun `sprint review skips blank labels and non numeric custom fields`() {
        val delegate = mock<com.stacklens.application.port.out.SprintReviewJiraPort>()
        val adapter = McpAwareSprintReviewJiraAdapter(mcpRepo, gateway, mapper, delegate)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                """{"total":1,"issues":[{"key":"A-1","fields":{"summary":"s",
                   "labels":["good","","   "],
                   "customfield_10001":null,
                   "customfield_10002":3,
                   "customfield_10003":{"value":"x"}}}]}""",
            ),
        )

        val tickets: List<SprintReviewTicket> = adapter.fetchSprintTickets(jiraConfig, 9L)

        assertEquals(1, tickets.size)
        assertEquals(listOf("good"), tickets[0].labels)
        assertEquals(3.0, tickets[0].storyPoints!!)
    }

    @Test
    fun `ticket summary renders empty collections when the payload carries no arrays`() {
        val delegate = mock<JiraTicketSummaryJiraPort>()
        val adapter = McpAwareJiraTicketSummaryAdapter(mcpRepo, gateway, mapper, delegate)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult("""{"key":"PROJ-5","labels":[],"comments":[]}"""),
        )

        val ticket = adapter.fetchTicketWithComments(jiraConfig, "PROJ-5")

        assertEquals("PROJ-5", ticket!!.key)
        assertTrue(ticket.labels.isEmpty())
        assertTrue(ticket.comments.isEmpty())
    }

    @Test
    fun `ticket summary falls back to the native adapter when the payload is no json`() {
        val delegate = mock<JiraTicketSummaryJiraPort>()
        val adapter = McpAwareJiraTicketSummaryAdapter(mcpRepo, gateway, mapper, delegate)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult("definitely not json {"))

        assertNull(adapter.fetchTicketWithComments(jiraConfig, "PROJ-6"))
    }

    @Test
    fun `ticket summary falls back to the native adapter when the tool call throws`() {
        val delegate = mock<JiraTicketSummaryJiraPort>()
        val adapter = McpAwareJiraTicketSummaryAdapter(mcpRepo, gateway, mapper, delegate)
        whenever(gateway.callTool(any(), any(), any())).thenThrow(RuntimeException("socket closed"))

        assertNull(adapter.fetchTicketWithComments(jiraConfig, "PROJ-7"))
    }

    @Test
    fun `gitlab routing keeps the delegate while the mcp process is down`() {
        val delegate = mock<com.stacklens.application.port.out.repo.GitlabPort>()
        val routed = mock<com.stacklens.application.port.out.repo.GitlabPort>()
        val processControl = mock<McpProcessControlPort>()
        whenever(processControl.status())
            .thenReturn(
                McpProcessControlPort.Status(
                    running = false,
                    pid = null,
                    startedAt = null,
                    stoppedAt = null,
                    exitCode = null,
                    message = "",
                    logs = emptyList(),
                ),
            )
        val adapter =
            McpAwareGitlabAdapter(
                mcpRepo = mcpRepo,
                gateway = gateway,
                objectMapper = mapper,
                delegate = delegate,
                portFactory = { routed },
                processControl = processControl,
            )
        val resolvePort = McpAwareGitlabAdapter::class.declaredFunctions.first { it.name == "resolvePort" }
        resolvePort.javaMethod!!.isAccessible = true

        val port =
            resolvePort.javaMethod!!.invoke(
                adapter,
                GitlabConfig(name = "g", baseUrl = "https://gitlab.example.com", accessToken = "tok"),
            )

        assertSame(delegate, port)
    }
}
