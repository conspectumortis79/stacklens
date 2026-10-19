package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.SprintReviewAdfDocument
import com.stacklens.application.port.out.SprintReviewJiraPort
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import com.stacklens.domain.model.SprintReviewRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.mockingDetails
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class McpAwareSprintReviewJiraAdapterMappingTest {
    private val mcpRepo: McpServerConfigPort = mock()
    private val gateway: McpGatewayPort = mock()
    private val delegate: SprintReviewJiraPort = mock()
    private val adapter = McpAwareSprintReviewJiraAdapter(mcpRepo, gateway, ObjectMapper(), delegate)

    private val config = JiraConfig(id = 7L, name = "banana", baseUrl = "https://jira.test/", apiToken = "tok", cloudMode = true)

    private fun routingToSameTenant() {
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

    private fun respondWith(vararg payloads: String) {
        val queued = payloads.toMutableList()
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            McpToolResult(text = queued.removeAt(0), isError = false)
        }
    }

    private fun page(
        vararg issues: String,
        total: Int = issues.size,
    ): String = """{"issues":[${issues.joinToString(",")}],"total":$total}"""

    private fun issue(
        key: String = "PROJ-1",
        fields: String,
    ): String = """{"key":"$key","fields":{$fields}}"""

    @Test fun `fetchSprintTickets maps a fully populated jira issue`() {
        routingToSameTenant()
        respondWith(
            page(
                issue(
                    fields =
                        """
                        "summary":"Build the wizard",
                        "description":{"version":1,"type":"doc"},
                        "labels":["ui"," ","wizard"],
                        "issuetype":{"name":"Story"},
                        "status":{"name":"In Progress"},
                        "assignee":{"displayName":"Alice"},
                        "priority":{"name":"High"},
                        "customfield_10016":8
                        """.trimIndent(),
                ),
            ),
        )

        val tickets = adapter.fetchSprintTickets(config, 30027L)

        assertEquals(1, tickets.size)
        val ticket = tickets.single()
        assertEquals("PROJ-1", ticket.key)
        assertEquals("Build the wizard", ticket.summary)
        assertEquals("""{"version":1,"type":"doc"}""", ticket.description)
        assertEquals("https://jira.test/browse/PROJ-1", ticket.url)
        assertEquals(listOf("ui", "wizard"), ticket.labels)
        assertEquals(8.0, ticket.storyPoints)
        assertEquals(listOf("customfield_10016: 8.0"), ticket.numericCustomFields)
        verify(delegate, never()).fetchSprintTickets(any(), any())
    }

    @Test fun `fetchSprintTickets works without a jira configuration and without an url`() {
        routingToSameTenant()
        respondWith(
            page(
                issue(
                    key = "PROJ-9",
                    fields =
                        """
                        "summary":"No tenant",
                        "issuetype":{"name":"Task"},
                        "assignee":{"displayName":"  "},
                        "priority":{"name":""}
                        """.trimIndent(),
                ),
            ),
        )

        val ticket = adapter.fetchSprintTickets(null, 1L).single()

        assertEquals("", ticket.url)
        assertEquals("", ticket.description)
        assertEquals(null, ticket.assignee)
        assertEquals(null, ticket.priority)
        assertEquals(null, ticket.storyPoints)
    }

    @Test fun `fetchSprintTickets skips subtasks and keeps paging until the total is reached`() {
        routingToSameTenant()
        val firstPage = (1..50).map { issue(key = "P-$it", fields = """"summary":"s $it"""") }
        val secondPage =
            (51..60).map { issue(key = "P-$it", fields = """"summary":"s $it"""") } +
                issue(
                    key = "P-SUB",
                    fields = """"summary":"child","issuetype":{"name":"Sub-task","subtask":true}""",
                )
        respondWith(
            page(*firstPage.toTypedArray(), total = 61),
            page(*secondPage.toTypedArray(), total = 61),
        )

        val tickets = adapter.fetchSprintTickets(config, 42L)

        assertEquals(60, tickets.size)
        assertTrue(tickets.none { it.key == "P-SUB" })
        assertEquals(2, gateway.invocationCount("callTool"))
    }

    private fun McpGatewayPort.invocationCount(methodName: String): Int = mockingDetails(this).invocations.count { it.method.name == methodName }

    @Test fun `fetchSprintTickets stops when the payload has no issue array`() {
        routingToSameTenant()
        respondWith("""{"issues":{"nope":true},"total":9}""")

        assertTrue(adapter.fetchSprintTickets(config, 1L).isEmpty())
    }

    @Test fun `fetchSprintTickets stops when the issue array is empty`() {
        routingToSameTenant()
        respondWith("""{"issues":[],"total":9}""")

        assertTrue(adapter.fetchSprintTickets(config, 1L).isEmpty())
    }

    @Test fun `fetchSprintTickets falls back to the native adapter for unreadable responses`() {
        routingToSameTenant()
        whenever(delegate.fetchSprintTickets(any(), any())).thenReturn(emptyList())
        respondWith("this is not json")

        assertEquals(0, adapter.fetchSprintTickets(config, 1L).size)
    }

    @Test fun `fetchSprintTickets falls back to the native adapter when the tool reports an error`() {
        routingToSameTenant()
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(text = "jira is down", isError = true))
        whenever(delegate.fetchSprintTickets(any(), any())).thenReturn(emptyList())

        assertTrue(adapter.fetchSprintTickets(config, 1L).isEmpty())
    }

    @Test fun `story points are read from numeric textual and nested custom fields`() {
        routingToSameTenant()
        respondWith(
            page(
                issue(key = "A", fields = """"summary":"a","customfield_10016":null,"customfield_10026":3.5"""),
                issue(key = "B", fields = """"summary":"b","customfield_10016":"4,25""""),
                issue(key = "C", fields = """"summary":"c","customfield_10016":{"value":" "}"""),
                issue(key = "D", fields = """"summary":"d","customfield_10016":{"estimate":{"deep":1}},"customfield_10026":"not a number""""),
                issue(key = "E", fields = """"summary":"e","customfield_10016":{},"customfield_10026":{},"customfield_10004":{"number":12}"""),
                issue(key = "F", fields = """"summary":"f","customfield_10016":true """),
                issue(key = "G", fields = """"summary":"g","labels":[null,"","kept"],"customfield_10016":"","customfield_10026":"2""""),
            ),
        )

        val tickets = adapter.fetchSprintTickets(config, 1L)

        assertEquals(listOf(3.5, 4.25, null, null, 12.0, null, 2.0), tickets.sortedBy { it.key }.map { it.storyPoints })
        assertEquals(listOf("kept"), tickets.first { it.key == "G" }.labels)
    }

    @Test fun `numeric custom fields are filtered and sorted`() {
        routingToSameTenant()
        respondWith(
            page(
                issue(
                    fields =
                        """
                        "summary":"mixed",
                        "customfield_10002":"1,5",
                        "customfield_10003":"",
                        "customfield_10004":{"amount":"7"},
                        "customfield_10005":{"value":true},
                        "customfield_10006":null,
                        "customfield_10007":false,
                        "customfield_10008":{"value":" ","number":9},
                        "notAField":42
                        """.trimIndent(),
                ),
            ),
        )

        val ticket = adapter.fetchSprintTickets(config, 1L).single()

        assertEquals(
            listOf(
                "customfield_10002: 1.5",
                "customfield_10004: 7.0",
                "customfield_10008: 9.0",
            ),
            ticket.numericCustomFields,
        )
    }

    @Test fun `renderAdfDocument delegates to the native adapter`() {
        val rows = emptyList<SprintReviewRow>()
        val document = SprintReviewAdfDocument("""{"type":"doc"}""")
        whenever(delegate.renderAdfDocument(rows, true, "Sprint 1")).thenReturn(document)

        assertEquals(document, adapter.renderAdfDocument(rows, true, "Sprint 1"))
        verify(delegate).renderAdfDocument(rows, true, "Sprint 1")
    }

    @Test fun `appendAdfToDescription delegates without a tenant comparison when no config is given`() {
        routingToSameTenant()
        whenever(delegate.appendAdfToDescription(anyOrNull(), any(), any())).thenReturn("ok")

        assertEquals("ok", adapter.appendAdfToDescription(null, "PROJ-1", SprintReviewAdfDocument("""{"type":"doc"}""")))
        assertEquals("ok", adapter.appendAdfToDescription(config, "PROJ-1", SprintReviewAdfDocument("""{"type":"doc"}""")))
        verify(delegate, times(2)).appendAdfToDescription(anyOrNull(), any(), any())
    }
}
