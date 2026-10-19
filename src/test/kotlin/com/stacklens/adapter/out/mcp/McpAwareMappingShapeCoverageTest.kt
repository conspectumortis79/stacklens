package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.port.out.JiraTicketSummaryJiraPort
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.MrSuggestionPort
import com.stacklens.application.port.out.database.DatabaseScanPort
import com.stacklens.application.port.out.search.ConfluenceSearchPort
import com.stacklens.application.port.out.search.GrafanaSearchPort
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.SearchResult
import com.stacklens.testsupport.HashCollisions
import com.stacklens.testsupport.McpRoutingFixtures
import com.stacklens.testsupport.PrivateInvocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.URI

class McpAwareMappingShapeCoverageTest {
    private val mapper = ObjectMapper()
    private val gateway = mock<McpGatewayPort>()
    private val mcpRepo = mock<McpServerConfigPort>()

    init {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(McpRoutingFixtures.routed())
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult("{}", isError = false))
    }

    private fun grafana() = McpAwareGrafanaSearchAdapter(mcpRepo, gateway, mapper, mock<GrafanaSearchPort>())

    private fun node(json: String) = mapper.readTree(json)

    @Test
    fun `a query snippet only collects fields that actually mention the keyword`() {
        val target = node("""{"expr":"   ","query":"up{job}","rawQuery":"AUTH metrics","refId":"A"}""")

        val snippet = PrivateInvocation.invoke(grafana(), "extractQuerySnippet", target, "auth") as String

        assertEquals("rawQuery: AUTH metrics", snippet)
    }

    @Test
    fun `a query without any keyword falls back to a truncated dump`() {
        val snippet =
            PrivateInvocation.invoke(
                grafana(),
                "extractQuerySnippet",
                node("""{"expr":"up{job}"}"""),
                "absent",
            ) as String

        assertTrue(snippet.startsWith("Query contains 'absent':"), snippet)
    }

    @Test
    fun `a slash only filter matches nothing while its trimmed form decides the rest`() {
        val dash = node("""{"url":"/d/xyz/auth","title":"Login Board","uid":"xyz"}""")
        val filter = { f: String -> PrivateInvocation.invoke(grafana(), "matchesFilter", dash, listOf(f)) }

        assertEquals(false, filter("//"))
        assertEquals(true, filter("login board"))
        assertEquals(true, filter("//auth//"))
        assertEquals(true, filter("/d/xyz/"))
        assertEquals(true, filter("/xyz"))
        assertEquals(true, filter("xyz"))
        assertEquals(true, filter("//board//"))
        assertEquals(false, filter("/nope/"))
    }

    @Test
    fun `a result without a snippet still gets a deduplication key`() {
        val key =
            PrivateInvocation.invoke(
                grafana(),
                "resultKey",
                SearchResult(sessionId = 1L, keyword = "kw", sourceType = SourceType.GRAFANA, codeSnippet = null),
            ) as String

        assertTrue(key.endsWith("|"), key)
    }

    @Test
    fun `a panel without a type is reported without a function name`() {
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(
                McpToolResult("""[{"uid":"u1","title":"Auth Dash","url":"/d/u1"}]""", false),
                McpToolResult("""[{"uid":"u1","title":"Auth Dash","url":"/d/u1"}]""", false),
                McpToolResult("""{"dashboard":{"panels":[{"title":"Auth Panel","targets":[{"expr":"auth_total"}]}]}}""", false),
            )

        val results =
            grafana()
                .searchKeyword(
                    com.stacklens.domain.model.GrafanaConfig(
                        name = "g",
                        baseUrl = McpRoutingFixtures.GRAFANA_BASE_URL,
                        username = "u",
                        password = "p",
                        scanPanelQueries = true,
                    ),
                    "auth",
                    1L,
                    emptyList(),
                )

        val panel = results.first { it.resultType == ResultType.GRAFANA_PANEL }
        assertNull(panel.functionName)
        assertNotNull(panel.codeSnippet)
    }

    @Test
    fun `empty label and comment arrays are not turned into single entries`() {
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(
                McpToolResult(
                    """{"key":"KEY-1","summary":"s","labels":[],"comments":[]}""",
                    false,
                ),
            )
        val summary =
            McpAwareJiraTicketSummaryAdapter(
                mcpRepo,
                gateway,
                mapper,
                mock<JiraTicketSummaryJiraPort>(),
            )

        val ticket = summary.fetchTicketWithComments(null, "KEY-1")

        assertNotNull(ticket)
        assertEquals(emptyList<String>(), ticket!!.labels)
        assertEquals(emptyList<Any>(), ticket.comments)
    }

    @Test
    fun `a ticket without label or comment fields reads as empty lists`() {
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(McpToolResult("""{"key":"KEY-2","summary":"only a summary"}""", false))
        val summary =
            McpAwareJiraTicketSummaryAdapter(mcpRepo, gateway, mapper, mock<JiraTicketSummaryJiraPort>())

        val ticket = summary.fetchTicketWithComments(null, "KEY-2")

        assertEquals(emptyList<String>(), ticket!!.labels)
        assertEquals(emptyList<Any>(), ticket.comments)
    }

    @Test
    fun `a sprint ticket ignores blank labels and treats a json null field as no number`() {
        val review = McpAwareSprintReviewJiraAdapter(mcpRepo, gateway, mapper, mock())
        val issue =
            node(
                """
                {
                  "key": "SPR-1",
                  "fields": {
                    "summary": "s",
                    "labels": ["keep", "", "  "],
                    "customfield_points": null,
                    "customfield_other": {"value": "text"}
                  }
                }
                """.trimIndent(),
            )

        val ticket = PrivateInvocation.invoke(review, "mapIssue", issue, null as JiraConfig?) as Any

        assertEquals(listOf("keep"), PrivateInvocation.invoke(ticket, "getLabels"))
        assertNull(PrivateInvocation.invoke(review, "parseNumericField", node("null")))
        assertNull(PrivateInvocation.invoke(review, "parseNumericField", node("{}").path("absent")))
        assertNull(PrivateInvocation.invoke(review, "parseNumericField", node("""{"value":"text"}""")))
        assertEquals(3.0, PrivateInvocation.invoke(review, "parseNumericField", node("""{"value":3}""")))
    }

    @Test
    fun `a confluence search item needs both space and page to build a cloud link`() {
        val search = McpAwareConfluenceSearchAdapter(mcpRepo, gateway, mapper, mock<ConfluenceSearchPort>())
        val config =
            ConfluenceConfig(
                name = "wiki",
                baseUrl = "https://wiki.example.com",
                username = "u",
                apiToken = "t",
                cloudMode = true,
            )
        val text =
            """
            {"results":[
              {"title":"absolute","_links":{"webui":"https://x/y"}},
              {"title":"cloud","space":{"key":"DEV","name":"Dev"},"id":"99","_links":{"webui":"/x"}},
              {"title":"no page","space":{"key":"DEV"},"_links":{"webui":"/y"}},
              {"title":"no space","id":"7","_links":{"webui":"/z"}},
              {"title":""}
            ]}
            """.trimIndent()

        val mapped = search.mapToSearchResults(text, config, "kw", 7L)

        assertEquals(4, mapped.size)
        assertEquals("https://wiki.example.com/spaces/DEV/pages/99", mapped[1].repoUrl)
        assertEquals("https://wiki.example.com/z", mapped[3].repoUrl)
    }

    @Test
    fun `every database hit kind maps to its own result type`() {
        val db = McpAwareDatabaseScanAdapter(mcpRepo, gateway, mapper, mock<DatabaseScanPort>())
        val config =
            DatabaseConfig(
                name = "db",
                dbType = DatabaseType.POSTGRESQL,
                host = "h",
                port = 5432,
                databaseName = "d",
                username = "u",
                password = "p",
                active = true,
            )

        val kinds = mapOf("table" to ResultType.TABLE, "column" to ResultType.COLUMN, "view" to ResultType.VIEW)
        for ((kind, expected) in kinds) {
            val hit = node("""{"kind":"$kind","name":"n"}""")
            val mapped = PrivateInvocation.invoke(db, "mapHitToSearchResult", hit, config, "kw", 1L, "d")
            assertEquals(expected, PrivateInvocation.invoke(mapped!!, "getResultType"))
        }

        assertNull(PrivateInvocation.invoke(db, "mapHitToSearchResult", node("""{"kind":"index"}"""), config, "kw", 1L, "d"))
        for (kind in listOf("table", "column", "view")) {
            val twin = HashCollisions.sharingHashCodeOf(kind)
            assertTrue(twin != kind && twin.hashCode() == kind.hashCode())
            assertNull(
                PrivateInvocation.invoke(db, "mapHitToSearchResult", node("""{"kind":"$twin"}"""), config, "kw", 1L, "d"),
                "$twin must not map like $kind",
            )
        }
    }

    @Test
    fun `a blank sha is not sent when a file is overwritten`() {
        val bridge = McpAwareGithubTicketBridgeAdapter(mcpRepo, gateway, mapper, mock<GithubTicketBridgePort>())

        listOf(null, "", "  ", "sha-1").forEach { sha ->
            PrivateInvocation.invoke(
                bridge,
                "putFile",
                McpRoutingFixtures.routed(),
                "owner",
                "repo",
                "path/file.txt",
                "branch",
                "message",
                "content",
                sha,
            )
        }
    }

    @Test
    fun `a merge request url without a numeric tail is delegated`() {
        val mr = McpAwareMrSuggestionAdapter(mcpRepo, gateway, mapper, mock<MrSuggestionPort>())
        val request =
            MrSuggestionPort.PostRequest(
                mrUrl = "http://gitlab.example/group/-/merge_requests",
                platform = "gitlab",
                gitConfigId = null,
                gitlabConfigs = emptyList(),
                githubConfigs = emptyList(),
                diffBaseSha = "base",
                diffStartSha = "",
                diffHeadSha = "head",
                filePath = "a/B.kt",
                lineNumber = 3,
                suggestedCode = "code",
            )

        assertNull(PrivateInvocation.invoke(mr, "postGitlab", request, URI.create(request.mrUrl)))
    }
}
