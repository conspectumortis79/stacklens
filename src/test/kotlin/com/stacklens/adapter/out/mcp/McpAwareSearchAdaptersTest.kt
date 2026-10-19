package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.search.ConfluenceSearchPort
import com.stacklens.application.port.out.search.JiraSearchPort
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import com.stacklens.domain.model.SearchResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class McpAwareSearchAdaptersTest {
    private val objectMapper = ObjectMapper().apply { findAndRegisterModules() }

    private fun activeMcp(
        jiraBaseUrl: String = "http://localhost:8081",
        confluenceBaseUrl: String = "http://localhost:8081",
    ) = McpServerConfig(
        id = 1L,
        active = true,
        routeJira = true,
        routeConfluence = true,
        jiraBaseUrl = jiraBaseUrl,
        jiraApiToken = "token",
        confluenceBaseUrl = confluenceBaseUrl,
        confluenceApiToken = "token",
    )

    private fun jiraConfig(baseUrl: String = "http://localhost:8081") = JiraConfig(id = 1L, name = "Demo", baseUrl = baseUrl, apiToken = "tok")

    private fun confluenceConfig(baseUrl: String = "http://localhost:8081") = ConfluenceConfig(id = 1L, name = "Demo", baseUrl = baseUrl, username = "u", apiToken = "tok")

    private fun emptyResults(): List<SearchResult> = emptyList()

    @Test
    fun `Jira - delegates to native adapter when MCP is not active`() {
        val mcpRepo: McpServerConfigPort = mock()
        val gateway: McpGatewayPort = mock()
        val delegate: JiraSearchPort = mock()
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(delegate.searchKeyword(any(), any(), any(), any(), any(), any())).thenReturn(emptyResults())

        val adapter = McpAwareJiraSearchAdapter(mcpRepo, gateway, objectMapper, delegate)
        val config = jiraConfig()
        adapter.searchKeyword(config, "foo", emptyList(), 1L, emptyList(), emptyList())

        verify(delegate).searchKeyword(eq(config), eq("foo"), eq(emptyList()), eq(1L), eq(emptyList()), eq(emptyList()))
        verify(gateway, never()).callTool(any(), any(), any())
    }

    @Test
    fun `Jira - delegates to native adapter when config URL does not match MCP jiraBaseUrl`() {
        val mcpRepo: McpServerConfigPort = mock()
        val gateway: McpGatewayPort = mock()
        val delegate: JiraSearchPort = mock()

        val mcp = activeMcp(jiraBaseUrl = "http://localhost:8081")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        whenever(delegate.searchKeyword(any(), any(), any(), any(), any(), any())).thenReturn(emptyResults())

        val adapter = McpAwareJiraSearchAdapter(mcpRepo, gateway, objectMapper, delegate)
        val config = jiraConfig(baseUrl = "https://banana.atlassian.net")
        adapter.searchKeyword(config, "Tageslisten", emptyList(), 1L, emptyList(), emptyList())

        verify(delegate).searchKeyword(eq(config), eq("Tageslisten"), any(), any(), any(), any())
        verify(gateway, never()).callTool(any(), any(), any())
    }

    @Test
    fun `Jira - routes through MCP when config URL matches MCP jiraBaseUrl`() {
        val mcpRepo: McpServerConfigPort = mock()
        val gateway: McpGatewayPort = mock()
        val delegate: JiraSearchPort = mock()

        val mcp = activeMcp(jiraBaseUrl = "http://localhost:8081")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        val successResult = McpToolResult(text = """{"issues":[]}""", isError = false)
        whenever(gateway.callTool(eq(mcp), eq("jira_search_issues"), any())).thenReturn(successResult)

        val adapter = McpAwareJiraSearchAdapter(mcpRepo, gateway, objectMapper, delegate)
        val config = jiraConfig(baseUrl = "http://localhost:8081")
        val results = adapter.searchKeyword(config, "payment", emptyList(), 1L, emptyList(), emptyList())

        verify(gateway).callTool(eq(mcp), eq("jira_search_issues"), any())
        verify(delegate, never()).searchKeyword(any(), any(), any(), any(), any(), any())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `Jira - URL matching is case-insensitive and strips trailing slash`() {
        val mcpRepo: McpServerConfigPort = mock()
        val gateway: McpGatewayPort = mock()
        val delegate: JiraSearchPort = mock()

        val mcp = activeMcp(jiraBaseUrl = "http://localhost:8081/")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        val successResult = McpToolResult(text = """{"issues":[]}""", isError = false)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(successResult)

        val adapter = McpAwareJiraSearchAdapter(mcpRepo, gateway, objectMapper, delegate)
        val config = jiraConfig(baseUrl = "HTTP://LOCALHOST:8081")
        adapter.searchKeyword(config, "x", emptyList(), 1L, emptyList(), emptyList())

        verify(gateway).callTool(any(), eq("jira_search_issues"), any())
        verify(delegate, never()).searchKeyword(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `Jira - falls back to native adapter when MCP gateway returns error`() {
        val mcpRepo: McpServerConfigPort = mock()
        val gateway: McpGatewayPort = mock()
        val delegate: JiraSearchPort = mock()

        val mcp = activeMcp()
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(text = "server error", isError = true))
        whenever(delegate.searchKeyword(any(), any(), any(), any(), any(), any())).thenReturn(emptyResults())

        val adapter = McpAwareJiraSearchAdapter(mcpRepo, gateway, objectMapper, delegate)
        adapter.searchKeyword(jiraConfig(), "foo", emptyList(), 1L, emptyList(), emptyList())

        verify(delegate).searchKeyword(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `Jira - falls back to native adapter when MCP gateway throws`() {
        val mcpRepo: McpServerConfigPort = mock()
        val gateway: McpGatewayPort = mock()
        val delegate: JiraSearchPort = mock()

        val mcp = activeMcp()
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        whenever(gateway.callTool(any(), any(), any())).thenThrow(RuntimeException("connection refused"))
        whenever(delegate.searchKeyword(any(), any(), any(), any(), any(), any())).thenReturn(emptyResults())

        val adapter = McpAwareJiraSearchAdapter(mcpRepo, gateway, objectMapper, delegate)
        adapter.searchKeyword(jiraConfig(), "foo", emptyList(), 1L, emptyList(), emptyList())

        verify(delegate).searchKeyword(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `buildJql - no filters produces text-only query`() {
        val adapter = McpAwareJiraSearchAdapter(mock(), mock(), objectMapper, mock())
        val jql = adapter.buildJql(emptyList(), "payment", emptyList(), emptyList())
        assertEquals("""(text ~ "payment" OR summary ~ "payment") ORDER BY updated DESC""", jql)
    }

    @Test
    fun `buildJql - project filter added`() {
        val adapter = McpAwareJiraSearchAdapter(mock(), mock(), objectMapper, mock())
        val jql = adapter.buildJql(listOf("PAY", "FA"), "csv", emptyList(), emptyList())
        assertTrue(jql.contains("""project in ("PAY","FA")"""))
    }

    @Test
    fun `buildJql - component and label filters added`() {
        val adapter = McpAwareJiraSearchAdapter(mock(), mock(), objectMapper, mock())
        val jql = adapter.buildJql(emptyList(), "fix", listOf("Backend"), listOf("release"))
        assertTrue(jql.contains("""component in ("Backend")"""))
        assertTrue(jql.contains("""labels in ("release")"""))
    }

    @Test
    fun `buildJql - project keys are upper-cased and blank entries filtered`() {
        val adapter = McpAwareJiraSearchAdapter(mock(), mock(), objectMapper, mock())
        val jql = adapter.buildJql(listOf("pay", " ", "fa"), "x", emptyList(), emptyList())
        assertTrue(jql.contains("""project in ("PAY","FA")"""))
    }

    @Test
    fun `buildJql - special chars in keyword are escaped`() {
        val adapter = McpAwareJiraSearchAdapter(mock(), mock(), objectMapper, mock())
        val jql = adapter.buildJql(emptyList(), """say "hello"""", emptyList(), emptyList())
        assertTrue(jql.contains("""text ~ "say \"hello\""""))
    }

    @Test
    fun `mapJiraIssuesToSearchResults - valid JSON produces search results`() {
        val adapter = McpAwareJiraSearchAdapter(mock(), mock(), objectMapper, mock())
        val json =
            """
            {
              "issues": [
                {
                  "key": "PAY-1",
                  "fields": {
                    "summary": "Fix payment bug",
                    "issuetype": {"name": "Bug"},
                    "status": {"name": "Open"},
                    "project": {"key": "PAY"}
                  }
                }
              ]
            }
            """.trimIndent()
        val config = jiraConfig("https://jira.example.com")
        val results = adapter.mapJiraIssuesToSearchResults(json, config, "payment", 42L)
        assertEquals(1, results.size)
        val r = results[0]
        assertEquals("PAY-1", r.filePath)
        assertEquals("Fix payment bug", r.codeSnippet)
        assertEquals("Bug", r.className)
        assertEquals("Open", r.functionName)
        assertEquals("https://jira.example.com/browse/PAY-1", r.repoUrl)
        assertEquals(42L, r.sessionId)
    }

    @Test
    fun `mapJiraIssuesToSearchResults - missing key entry is skipped`() {
        val adapter = McpAwareJiraSearchAdapter(mock(), mock(), objectMapper, mock())
        val json = """{"issues":[{"key":"","fields":{}}]}"""
        val results = adapter.mapJiraIssuesToSearchResults(json, jiraConfig(), "x", 1L)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `mapJiraIssuesToSearchResults - invalid JSON returns empty list`() {
        val adapter = McpAwareJiraSearchAdapter(mock(), mock(), objectMapper, mock())
        val results = adapter.mapJiraIssuesToSearchResults("not json", jiraConfig(), "x", 1L)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `mapJiraIssuesToSearchResults - missing issues field returns empty list`() {
        val adapter = McpAwareJiraSearchAdapter(mock(), mock(), objectMapper, mock())
        val results = adapter.mapJiraIssuesToSearchResults("{}", jiraConfig(), "x", 1L)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `Confluence - delegates to native adapter when MCP is not active`() {
        val mcpRepo: McpServerConfigPort = mock()
        val gateway: McpGatewayPort = mock()
        val delegate: ConfluenceSearchPort = mock()
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(delegate.searchKeyword(any(), any(), any(), any(), any())).thenReturn(emptyResults())

        val adapter = McpAwareConfluenceSearchAdapter(mcpRepo, gateway, objectMapper, delegate)
        val config = confluenceConfig()
        adapter.searchKeyword(config, "foo", emptyList(), 1L)

        verify(delegate).searchKeyword(eq(config), eq("foo"), eq(emptyList()), eq(1L), eq(0))
        verify(gateway, never()).callTool(any(), any(), any())
    }

    @Test
    fun `Confluence - delegates to native adapter when config URL does not match MCP confluenceBaseUrl`() {
        val mcpRepo: McpServerConfigPort = mock()
        val gateway: McpGatewayPort = mock()
        val delegate: ConfluenceSearchPort = mock()

        val mcp = activeMcp(confluenceBaseUrl = "http://localhost:8081")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        whenever(delegate.searchKeyword(any(), any(), any(), any(), any())).thenReturn(emptyResults())

        val adapter = McpAwareConfluenceSearchAdapter(mcpRepo, gateway, objectMapper, delegate)
        val config = confluenceConfig(baseUrl = "https://banana.atlassian.net")
        adapter.searchKeyword(config, "Tageslisten", emptyList(), 1L)

        verify(delegate).searchKeyword(eq(config), eq("Tageslisten"), any(), any(), any())
        verify(gateway, never()).callTool(any(), any(), any())
    }

    @Test
    fun `Confluence - routes through MCP when config URL matches MCP confluenceBaseUrl`() {
        val mcpRepo: McpServerConfigPort = mock()
        val gateway: McpGatewayPort = mock()
        val delegate: ConfluenceSearchPort = mock()

        val mcp = activeMcp(confluenceBaseUrl = "http://localhost:8081")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        val successResult = McpToolResult(text = """{"results":[]}""", isError = false)
        whenever(gateway.callTool(eq(mcp), eq("confluence_search"), any())).thenReturn(successResult)

        val adapter = McpAwareConfluenceSearchAdapter(mcpRepo, gateway, objectMapper, delegate)
        val config = confluenceConfig(baseUrl = "http://localhost:8081")
        val results = adapter.searchKeyword(config, "payment", emptyList(), 1L)

        verify(gateway).callTool(eq(mcp), eq("confluence_search"), any())
        verify(delegate, never()).searchKeyword(any(), any(), any(), any(), any())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `Confluence - URL matching is case-insensitive and strips trailing slash`() {
        val mcpRepo: McpServerConfigPort = mock()
        val gateway: McpGatewayPort = mock()
        val delegate: ConfluenceSearchPort = mock()

        val mcp = activeMcp(confluenceBaseUrl = "http://localhost:8081/")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(text = """{"results":[]}""", isError = false))

        val adapter = McpAwareConfluenceSearchAdapter(mcpRepo, gateway, objectMapper, delegate)
        adapter.searchKeyword(confluenceConfig(baseUrl = "HTTP://LOCALHOST:8081"), "x", emptyList(), 1L)

        verify(gateway).callTool(any(), eq("confluence_search"), any())
        verify(delegate, never()).searchKeyword(any(), any(), any(), any(), any())
    }

    @Test
    fun `Confluence - falls back to native adapter when MCP gateway returns error`() {
        val mcpRepo: McpServerConfigPort = mock()
        val gateway: McpGatewayPort = mock()
        val delegate: ConfluenceSearchPort = mock()

        val mcp = activeMcp()
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(text = "error", isError = true))
        whenever(delegate.searchKeyword(any(), any(), any(), any(), any())).thenReturn(emptyResults())

        val adapter = McpAwareConfluenceSearchAdapter(mcpRepo, gateway, objectMapper, delegate)
        adapter.searchKeyword(confluenceConfig(), "foo", emptyList(), 1L)

        verify(delegate).searchKeyword(any(), any(), any(), any(), any())
    }

    @Test
    fun `Confluence - falls back to native adapter when MCP gateway throws`() {
        val mcpRepo: McpServerConfigPort = mock()
        val gateway: McpGatewayPort = mock()
        val delegate: ConfluenceSearchPort = mock()

        val mcp = activeMcp()
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        whenever(gateway.callTool(any(), any(), any())).thenThrow(RuntimeException("timeout"))
        whenever(delegate.searchKeyword(any(), any(), any(), any(), any())).thenReturn(emptyResults())

        val adapter = McpAwareConfluenceSearchAdapter(mcpRepo, gateway, objectMapper, delegate)
        adapter.searchKeyword(confluenceConfig(), "foo", emptyList(), 1L)

        verify(delegate).searchKeyword(any(), any(), any(), any(), any())
    }

    @Test
    fun `buildCql - no space filter produces text-only query`() {
        val adapter = McpAwareConfluenceSearchAdapter(mock(), mock(), objectMapper, mock())
        val cql = adapter.buildCql("payment", emptyList())
        assertEquals("""text ~ "payment"""", cql)
    }

    @Test
    fun `buildCql - space keys appended`() {
        val adapter = McpAwareConfluenceSearchAdapter(mock(), mock(), objectMapper, mock())
        val cql = adapter.buildCql("foo", listOf("DEV", "QA"))
        assertEquals("""text ~ "foo" AND space in ("DEV","QA")""", cql)
    }

    @Test
    fun `buildCql - blank space keys are filtered out`() {
        val adapter = McpAwareConfluenceSearchAdapter(mock(), mock(), objectMapper, mock())
        val cql = adapter.buildCql("x", listOf("  ", "DEV"))
        assertEquals("""text ~ "x" AND space in ("DEV")""", cql)
    }

    @Test
    fun `buildCql - special chars in keyword are escaped`() {
        val adapter = McpAwareConfluenceSearchAdapter(mock(), mock(), objectMapper, mock())
        val cql = adapter.buildCql("""say "hello"""", emptyList())
        assertTrue(cql.contains("""text ~ "say \"hello\""""))
    }

    @Test
    fun `mapToSearchResults - valid JSON produces search results`() {
        val adapter = McpAwareConfluenceSearchAdapter(mock(), mock(), objectMapper, mock())
        val json =
            """
            {
              "results": [
                {
                  "title": "Payment Process",
                  "space": {"name": "Engineering", "key": "ENG"},
                  "_links": {"webui": "/display/ENG/Payment+Process"},
                  "excerpt": "Payment integration docs"
                }
              ]
            }
            """.trimIndent()
        val config = confluenceConfig("https://confluence.example.com")
        val results = adapter.mapToSearchResults(json, config, "payment", 7L)
        assertEquals(1, results.size)
        val r = results[0]
        assertEquals("Payment Process", r.filePath)
        assertEquals("Engineering", r.repoName)
        assertEquals("https://confluence.example.com/display/ENG/Payment+Process", r.repoUrl)
        assertEquals("Payment integration docs", r.codeSnippet)
        assertEquals(7L, r.sessionId)
    }

    @Test
    fun `mapToSearchResults - absolute webui link is used as-is`() {
        val adapter = McpAwareConfluenceSearchAdapter(mock(), mock(), objectMapper, mock())
        val json =
            """
            {
              "results": [
                {
                  "title": "Page",
                  "space": {"key": "SP"},
                  "_links": {"webui": "https://external.wiki/page/1"},
                  "excerpt": ""
                }
              ]
            }
            """.trimIndent()
        val results = adapter.mapToSearchResults(json, confluenceConfig(), "x", 1L)
        assertEquals("https://external.wiki/page/1", results[0].repoUrl)
    }

    @Test
    fun `mapToSearchResults - entry with blank title is skipped`() {
        val adapter = McpAwareConfluenceSearchAdapter(mock(), mock(), objectMapper, mock())
        val json = """{"results":[{"title":"","space":{},"_links":{}}]}"""
        val results = adapter.mapToSearchResults(json, confluenceConfig(), "x", 1L)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `mapToSearchResults - invalid JSON returns empty list`() {
        val adapter = McpAwareConfluenceSearchAdapter(mock(), mock(), objectMapper, mock())
        assertTrue(adapter.mapToSearchResults("bad json", confluenceConfig(), "x", 1L).isEmpty())
    }

    @Test
    fun `mapToSearchResults - missing results field returns empty list`() {
        val adapter = McpAwareConfluenceSearchAdapter(mock(), mock(), objectMapper, mock())
        assertTrue(adapter.mapToSearchResults("{}", confluenceConfig(), "x", 1L).isEmpty())
    }

    @Test
    fun `McpRouting activeIfRouted - returns null when repo throws`() {
        val mcpRepo: McpServerConfigPort = mock()
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenThrow(RuntimeException("DB down"))
        val result = McpRouting.activeIfRouted(mcpRepo) { true }
        assertTrue(result == null)
    }

    @Test
    fun `McpRouting activeIfRouted - returns null when config is not active`() {
        val mcpRepo: McpServerConfigPort = mock()
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(McpServerConfig(active = false, name = "x"))
        val result = McpRouting.activeIfRouted(mcpRepo) { true }
        assertTrue(result == null)
    }

    @Test
    fun `McpRouting activeIfRouted - returns null when predicate returns false`() {
        val mcpRepo: McpServerConfigPort = mock()
        val mcp = McpServerConfig(active = true, name = "x")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        val result = McpRouting.activeIfRouted(mcpRepo) { false }
        assertTrue(result == null)
    }

    @Test
    fun `McpRouting activeIfRouted - returns config when active and predicate passes`() {
        val mcpRepo: McpServerConfigPort = mock()
        val mcp = McpServerConfig(active = true, name = "x")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        val result = McpRouting.activeIfRouted(mcpRepo) { true }
        assertNotNull(result)
    }

    @Test
    fun `McpRouting jiraReady - returns false when routeJira is off`() {
        val cfg = McpServerConfig(routeJira = false, jiraBaseUrl = "http://x", jiraApiToken = "tok")
        assertTrue(!McpRouting.jiraReady(cfg))
    }

    @Test
    fun `McpRouting jiraReady - returns false when jiraBaseUrl is blank`() {
        val cfg = McpServerConfig(routeJira = true, jiraBaseUrl = "", jiraApiToken = "tok")
        assertTrue(!McpRouting.jiraReady(cfg))
    }

    @Test
    fun `McpRouting jiraReady - returns false when jiraApiToken is blank`() {
        val cfg = McpServerConfig(routeJira = true, jiraBaseUrl = "http://x", jiraApiToken = "")
        assertTrue(!McpRouting.jiraReady(cfg))
    }

    @Test
    fun `McpRouting jiraReady - returns true when all conditions met`() {
        val cfg = McpServerConfig(routeJira = true, jiraBaseUrl = "http://x", jiraApiToken = "tok")
        assertTrue(McpRouting.jiraReady(cfg))
    }

    @Test
    fun `McpRouting confluenceReady - returns false when routeConfluence is off`() {
        val cfg = McpServerConfig(routeConfluence = false, confluenceBaseUrl = "http://x", confluenceApiToken = "tok")
        assertTrue(!McpRouting.confluenceReady(cfg))
    }

    @Test
    fun `McpRouting confluenceReady - returns true when all conditions met`() {
        val cfg = McpServerConfig(routeConfluence = true, confluenceBaseUrl = "http://x", confluenceApiToken = "tok")
        assertTrue(McpRouting.confluenceReady(cfg))
    }

    @Test
    fun `McpRouting gitlabReady - returns false when routeGitlab is off`() {
        val cfg = McpServerConfig(routeGitlab = false, gitlabAccessToken = "tok")
        assertTrue(!McpRouting.gitlabReady(cfg))
    }

    @Test
    fun `McpRouting gitlabReady - returns true when all conditions met`() {
        val cfg = McpServerConfig(routeGitlab = true, gitlabAccessToken = "tok")
        assertTrue(McpRouting.gitlabReady(cfg))
    }

    @Test
    fun `McpRouting grafanaReady - returns false when routeGrafana is off`() {
        val cfg = McpServerConfig(routeGrafana = false, grafanaBaseUrl = "http://x")
        assertTrue(!McpRouting.grafanaReady(cfg))
    }

    @Test
    fun `McpRouting grafanaReady - returns false when grafanaBaseUrl is blank`() {
        val cfg = McpServerConfig(routeGrafana = true, grafanaBaseUrl = "")
        assertTrue(!McpRouting.grafanaReady(cfg))
    }

    @Test
    fun `McpRouting grafanaReady - returns true with blank apiKey (anonymous access)`() {
        val cfg = McpServerConfig(routeGrafana = true, grafanaBaseUrl = "http://grafana", grafanaApiKey = "")
        assertTrue(McpRouting.grafanaReady(cfg))
    }

    @Test
    fun `McpRouting parseJsonOrNull - returns null for invalid JSON`() {
        val result = McpRouting.parseJsonOrNull(objectMapper, "not json at all {{{")
        assertTrue(result == null)
    }

    @Test
    fun `McpRouting parseJsonOrNull - returns JsonNode for valid JSON`() {
        val result = McpRouting.parseJsonOrNull(objectMapper, """{"key":"value"}""")
        assertNotNull(result)
        assertEquals("value", result!!.path("key").asText())
    }

    private fun httpClient() = McpHttpClient(mock(), objectMapper, mock(), McpCircuitBreaker())

    @Test
    fun `resolveBaseUrl - localhost is rewritten to 127_0_0_1 outside Docker`() {
        assertEquals(
            "http://127.0.0.1:7456",
            httpClient().resolveBaseUrl("http://localhost:7456"),
        )
    }

    @Test
    fun `resolveBaseUrl - IPv6 loopback is rewritten to 127_0_0_1 outside Docker`() {
        assertEquals(
            "http://127.0.0.1:7456",
            httpClient().resolveBaseUrl("http://[::1]:7456"),
        )
    }

    @Test
    fun `resolveBaseUrl - localhost stays as IPv4 loopback`() {
        assertEquals(
            "http://127.0.0.1:7456",
            httpClient().resolveBaseUrl("http://localhost:7456"),
        )
    }

    @Test
    fun `resolveBaseUrl - 127_0_0_1 stays as IPv4 loopback`() {
        assertEquals(
            "http://127.0.0.1:7456",
            httpClient().resolveBaseUrl("http://127.0.0.1:7456"),
        )
    }

    @Test
    fun `resolveBaseUrl - external URL is unchanged`() {
        val url = "https://mcp.example.com:443"
        assertEquals(url, httpClient().resolveBaseUrl(url))
    }
}
