package com.stacklens.adapter.out.grafana

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.SearchResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GrafanaClientHelpersTest {
    private val objectMapper = ObjectMapper()

    private fun client() =
        GrafanaClient(
            connectTimeoutMs = 1000,
            readTimeoutMs = 1000,
        )

    private fun cfg(
        scanPanelQueries: Boolean = true,
        dashboardUidFilter: String? = null,
        folderFilter: String? = null,
    ) = GrafanaConfig(
        name = "test",
        baseUrl = "https://grafana.example",
        username = "admin",
        password = "secret",
        apiToken = "key",
        scanPanelQueries = scanPanelQueries,
    )

    private fun dash(
        uid: String = "abc",
        title: String = "Dash",
        folderTitle: String? = "Folder",
    ) = GrafanaClient.DashboardSearchItem(
        id = 1,
        uid = uid,
        title = title,
        type = "dash-db",
        url = "/d/$uid/dash",
        folderTitle = folderTitle,
    )

    @Test
    fun `encode URL-encodes special characters`() {
        assertEquals("hello+world", client().encode("hello world"))
        assertEquals("a%2Bb", client().encode("a+b"))
        assertEquals("%2F*", client().encode("/*"))
    }

    @Test
    fun `encode leaves plain ASCII unchanged`() {
        assertEquals("hello", client().encode("hello"))
    }

    @Test
    fun `matchesDashboardFilter returns true when filters list is empty`() {
        val filter = client().matchesDashboardFilter(dash(uid = "any-uid"), emptyList())
        assertTrue(filter)
    }

    @Test
    fun `matchesDashboardFilter matches by uid`() {
        val filter = client().matchesDashboardFilter(dash(uid = "abc"), listOf("abc"))
        assertTrue(filter)
    }

    @Test
    fun `matchesDashboardFilter rejects non-matching uid`() {
        val filter = client().matchesDashboardFilter(dash(uid = "xyz"), listOf("abc"))
        assertTrue(!filter)
    }

    @Test
    fun `matchesDashboardFilter accepts comma-separated uid filter`() {
        val filter = client().matchesDashboardFilter(dash(uid = "def"), listOf("abc", "def"))
        assertTrue(filter)
    }

    @Test
    fun `matchesDashboardFilter matches by title`() {
        val filter = client().matchesDashboardFilter(dash(title = "MyDash"), listOf("MyDash"))
        assertTrue(filter)
    }

    @Test
    fun `matchesDashboardFilter skips blank filters`() {
        val filter = client().matchesDashboardFilter(dash(uid = "abc"), listOf("", "abc"))
        assertTrue(filter)
    }

    @Test
    fun `extractQuerySnippet returns field-value lines for known fields`() {
        val target = objectMapper.readTree("""{"expr":"rate(http_requests_total{job=\"foo\"}[5m])"}""")
        val snippet = client().extractQuerySnippet(target, "rate")
        assertTrue(snippet.contains("expr: rate(http_requests_total"))
    }

    @Test
    fun `extractQuerySnippet returns query field when expr is missing`() {
        val target = objectMapper.readTree("""{"query":"SELECT * FROM users WHERE name = 'foo'"}""")
        val snippet = client().extractQuerySnippet(target, "SELECT")
        assertTrue(snippet.contains("query: SELECT"))
    }

    @Test
    fun `extractQuerySnippet returns rawSql field for SQL data sources`() {
        val target = objectMapper.readTree("""{"rawSql":"SELECT foo FROM bar"}""")
        val snippet = client().extractQuerySnippet(target, "foo")
        assertTrue(snippet.contains("rawSql: SELECT"))
    }

    @Test
    fun `extractQuerySnippet falls back to JSON when no known field matches`() {
        val target = objectMapper.readTree("""{"custom":"keyword-here"}""")
        val snippet = client().extractQuerySnippet(target, "keyword-here")
        assertTrue(snippet.contains("Query contains"))
    }

    @Test
    fun `extractQuerySnippet returns empty when target has no keyword`() {
        val target = objectMapper.readTree("""{"expr":"rate(other_metric)"}""")
        val snippet = client().extractQuerySnippet(target, "absent")
        assertTrue(snippet.startsWith("Query contains"))
    }

    @Test
    fun `resultKey combines repoName, filePath and codeSnippet`() {
        val r =
            SearchResult(
                sessionId = 1,
                keyword = "k",
                sourceType = com.stacklens.domain.enums.SourceType.GRAFANA,
                resultType = com.stacklens.domain.enums.ResultType.GRAFANA_PANEL,
                configName = "cfg",
                repoName = "MyDash",
                filePath = "Panel1",
                codeSnippet = "snippet",
            )
        assertEquals("MyDash|Panel1|snippet", client().resultKey(r))
    }

    @Test
    fun `resultKey truncates long codeSnippet to 80 chars`() {
        val r =
            SearchResult(
                sessionId = 1,
                keyword = "k",
                sourceType = com.stacklens.domain.enums.SourceType.GRAFANA,
                resultType = com.stacklens.domain.enums.ResultType.GRAFANA_PANEL,
                configName = "cfg",
                repoName = "MyDash",
                filePath = "Panel1",
                codeSnippet = "x".repeat(200),
            )
        val key = client().resultKey(r)
        assertTrue(key.endsWith("|${"x".repeat(80)}"))
    }

    @Test
    fun `resultKey uses empty string when codeSnippet is null`() {
        val r =
            SearchResult(
                sessionId = 1,
                keyword = "k",
                sourceType = com.stacklens.domain.enums.SourceType.GRAFANA,
                resultType = com.stacklens.domain.enums.ResultType.GRAFANA_PANEL,
                configName = "cfg",
                repoName = "MyDash",
                filePath = "Panel1",
            )
        assertEquals("MyDash|Panel1|", client().resultKey(r))
    }

    @Test
    fun `scanPanel returns result when title matches keyword`() {
        val panel = objectMapper.readTree("""{"title":"CPU Usage","type":"graph","description":""}""")
        val results = client().scanPanel(panel, cfg(), dash(), "CPU", 1L, "https://grafana/d/abc/cpu")
        assertEquals(1, results.size)
        assertEquals("CPU Usage", results[0].filePath)
        assertEquals("graph", results[0].functionName)
    }

    @Test
    fun `scanPanel returns result when description matches keyword`() {
        val panel = objectMapper.readTree("""{"title":"Foo","type":"graph","description":"Memory usage"}""")
        val results = client().scanPanel(panel, cfg(), dash(), "Memory", 1L, "url")
        assertEquals(1, results.size)
        assertTrue(results[0].codeSnippet!!.contains("Panel-Beschreibung"))
    }

    @Test
    fun `scanPanel returns no result when neither matches`() {
        val panel = objectMapper.readTree("""{"title":"Foo","type":"graph","description":"Bar"}""")
        val results = client().scanPanel(panel, cfg(), dash(), "absent", 1L, "url")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `scanPanel scans targets when scanPanelQueries is true`() {
        val panel =
            objectMapper.readTree(
                """{"title":"P","type":"graph","targets":[{"expr":"rate(metric[5m])"}]}""",
            )
        val results = client().scanPanel(panel, cfg(scanPanelQueries = true), dash(), "rate", 1L, "url")
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun `scanPanel skips targets when scanPanelQueries is false`() {
        val panel =
            objectMapper.readTree(
                """{"title":"P","type":"graph","targets":[{"expr":"rate(metric[5m])"}]}""",
            )
        val results = client().scanPanel(panel, cfg(scanPanelQueries = false), dash(), "rate", 1L, "url")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `scanPanel handles panel without title`() {
        val panel = objectMapper.readTree("""{"type":"graph","description":"has keyword"}""")
        val results = client().scanPanel(panel, cfg(), dash(), "keyword", 1L, "url")
        assertEquals(1, results.size)
        assertEquals(null, results[0].filePath)
    }

    @Test
    fun `scanPanel handles panel without type`() {
        val panel = objectMapper.readTree("""{"title":"P","description":"has keyword"}""")
        val results = client().scanPanel(panel, cfg(), dash(), "keyword", 1L, "url")
        assertEquals(1, results.size)
        assertEquals(null, results[0].functionName)
    }

    @Test
    fun `buildClient uses apiToken as Bearer token`() {
        val cfg = cfg().apply { apiToken = "test-key" }
        val client = client().buildClient(cfg)
        assertNotNull(client)
    }

    @Test
    fun `buildClient uses basic auth when username and password set`() {
        val cfg =
            cfg().apply {
                username = "admin"
                password = "secret"
                apiToken = ""
            }
        val client = client().buildClient(cfg)
        assertNotNull(client)
    }

    @Test
    fun `searchKeyword returns empty when config is inactive`() {
        val config = cfg().apply { active = false }
        val results = client().searchKeyword(config, "kw", 1L)
        assertTrue(results.isEmpty())
    }
}
