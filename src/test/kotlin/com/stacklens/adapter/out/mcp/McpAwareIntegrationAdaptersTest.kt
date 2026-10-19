package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.GitlabProjectInfo
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClient
import java.util.function.Function

class McpAwareIntegrationAdaptersTest {
    private val objectMapper = ObjectMapper().apply { findAndRegisterModules() }

    private fun activeMcp(gitlabBaseUrl: String = "http://localhost:3080") =
        McpServerConfig(
            id = 1L,
            active = true,
            routeGitlab = true,
            gitlabBaseUrl = gitlabBaseUrl,
            gitlabAccessToken = "mcp-token",
        )

    private fun matchingConfig() = GitlabConfig(id = 1L, name = "Demo", baseUrl = "http://localhost:3080", accessToken = "token")

    private fun nonMatchingConfig() = GitlabConfig(id = 2L, name = "fi", baseUrl = "https://gitlab.banana.de", accessToken = "fi-token")

    private fun projectInfo() =
        GitlabProjectInfo(
            id = 42L,
            name = "my-repo",
            pathWithNamespace = "group/my-repo",
            defaultBranch = "main",
            httpUrlToRepo = "https://gitlab.banana.de/my-repo.git",
        )

    private fun buildAdapter(
        mcpRepo: McpServerConfigPort,
        gateway: McpGatewayPort = mock(),
        delegate: GitlabPort,
        routed: GitlabPort,
    ): McpAwareGitlabAdapter {
        val portFactory = Function<WebClient.Builder, GitlabPort> { routed }
        return McpAwareGitlabAdapter(mcpRepo, gateway, objectMapper, delegate, portFactory, mock())
    }

    @Test
    fun `listAccessibleProjects - MCP active and host matches - routes through MCP`() {
        val mcpRepo: McpServerConfigPort = mock()
        val delegate: GitlabPort = mock()
        val routed: GitlabPort = mock()
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(routed.listAccessibleProjects(any())).thenReturn(listOf(projectInfo()))

        val adapter = buildAdapter(mcpRepo, delegate = delegate, routed = routed)
        val result = adapter.listAccessibleProjects(matchingConfig())

        assertEquals(listOf(projectInfo()), result)
        verify(routed).listAccessibleProjects(any())
        verify(delegate, never()).listAccessibleProjects(any())
    }

    @Test
    fun `listAccessibleProjects - MCP active but host does not match - uses native delegate`() {
        val mcpRepo: McpServerConfigPort = mock()
        val delegate: GitlabPort = mock()
        val routed: GitlabPort = mock()
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(delegate.listAccessibleProjects(any())).thenReturn(listOf(projectInfo()))

        val adapter = buildAdapter(mcpRepo, delegate = delegate, routed = routed)
        val result = adapter.listAccessibleProjects(nonMatchingConfig())

        assertEquals(listOf(projectInfo()), result)
        verify(delegate).listAccessibleProjects(any())
        verify(routed, never()).listAccessibleProjects(any())
    }

    @Test
    fun `listAccessibleProjects - MCP not active - uses native delegate`() {
        val mcpRepo: McpServerConfigPort = mock()
        val delegate: GitlabPort = mock()
        val routed: GitlabPort = mock()
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(delegate.listAccessibleProjects(any())).thenReturn(emptyList())

        val adapter = buildAdapter(mcpRepo, delegate = delegate, routed = routed)
        adapter.listAccessibleProjects(nonMatchingConfig())

        verify(delegate).listAccessibleProjects(any())
        verify(routed, never()).listAccessibleProjects(any())
    }

    @Test
    fun `fetchProjectFiles - MCP active and host matches - routes through MCP`() {
        val mcpRepo: McpServerConfigPort = mock()
        val delegate: GitlabPort = mock()
        val routed: GitlabPort = mock()
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(routed.fetchProjectFiles(any(), any(), any(), any())).thenReturn(listOf("path/to/file.kt" to "content"))

        val adapter = buildAdapter(mcpRepo, delegate = delegate, routed = routed)
        val result = adapter.fetchProjectFiles(matchingConfig(), 1L)

        assertEquals(1, result.size)
        verify(routed).fetchProjectFiles(any(), any(), any(), any())
        verify(delegate, never()).fetchProjectFiles(any(), any(), any(), any())
    }

    @Test
    fun `fetchProjectFiles - MCP active but host does not match - uses native delegate`() {
        val mcpRepo: McpServerConfigPort = mock()
        val delegate: GitlabPort = mock()
        val routed: GitlabPort = mock()
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(delegate.fetchProjectFiles(any(), any(), any(), any())).thenReturn(listOf("src/Main.kt" to "fun main(){}"))

        val adapter = buildAdapter(mcpRepo, delegate = delegate, routed = routed)
        val result = adapter.fetchProjectFiles(nonMatchingConfig(), 2L)

        assertEquals(1, result.size)
        verify(delegate).fetchProjectFiles(any(), any(), any(), any())
        verify(routed, never()).fetchProjectFiles(any(), any(), any(), any())
    }

    @Test
    fun `listProjectFilePaths - MCP active and host matches - routes through MCP`() {
        val mcpRepo: McpServerConfigPort = mock()
        val delegate: GitlabPort = mock()
        val routed: GitlabPort = mock()
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(routed.listProjectFilePaths(any(), any())).thenReturn(listOf("src/Foo.kt"))

        val adapter = buildAdapter(mcpRepo, delegate = delegate, routed = routed)
        adapter.listProjectFilePaths(matchingConfig(), 1L)

        verify(routed).listProjectFilePaths(any(), any())
        verify(delegate, never()).listProjectFilePaths(any(), any())
    }

    @Test
    fun `listProjectFilePaths - MCP active but host does not match - uses native delegate`() {
        val mcpRepo: McpServerConfigPort = mock()
        val delegate: GitlabPort = mock()
        val routed: GitlabPort = mock()
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(delegate.listProjectFilePaths(any(), any())).thenReturn(listOf("src/Bar.kt"))

        val adapter = buildAdapter(mcpRepo, delegate = delegate, routed = routed)
        adapter.listProjectFilePaths(nonMatchingConfig(), 2L)

        verify(delegate).listProjectFilePaths(any(), any())
        verify(routed, never()).listProjectFilePaths(any(), any())
    }

    @Test
    fun `fetchProjectFilesForPath - MCP active and host matches - routes through MCP`() {
        val mcpRepo: McpServerConfigPort = mock()
        val delegate: GitlabPort = mock()
        val routed: GitlabPort = mock()
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(routed.fetchProjectFilesForPath(any(), any(), any(), any(), any())).thenReturn(emptyList())

        val adapter = buildAdapter(mcpRepo, delegate = delegate, routed = routed)
        adapter.fetchProjectFilesForPath(matchingConfig(), 1L, "src/service")

        verify(routed).fetchProjectFilesForPath(any(), any(), any(), any(), any())
        verify(delegate, never()).fetchProjectFilesForPath(any(), any(), any(), any(), any())
    }

    @Test
    fun `fetchProjectFilesForPath - MCP active but host does not match - uses native delegate`() {
        val mcpRepo: McpServerConfigPort = mock()
        val delegate: GitlabPort = mock()
        val routed: GitlabPort = mock()
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(delegate.fetchProjectFilesForPath(any(), any(), any(), any(), any())).thenReturn(emptyList())

        val adapter = buildAdapter(mcpRepo, delegate = delegate, routed = routed)
        adapter.fetchProjectFilesForPath(nonMatchingConfig(), 2L, "src/service")

        verify(delegate).fetchProjectFilesForPath(any(), any(), any(), any(), any())
        verify(routed, never()).fetchProjectFilesForPath(any(), any(), any(), any(), any())
    }

    @Test
    fun `searchKeyword - MCP active and host matches - routes through MCP`() {
        val mcpRepo: McpServerConfigPort = mock()
        val delegate: GitlabPort = mock()
        val routed: GitlabPort = mock()
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(routed.searchKeyword(any(), any(), any(), anyOrNull(), any())).thenReturn(emptyList())

        val adapter = buildAdapter(mcpRepo, delegate = delegate, routed = routed)
        adapter.searchKeyword(matchingConfig(), "payment", 1L)

        verify(routed).searchKeyword(any(), any(), any(), anyOrNull(), any())
        verify(delegate, never()).searchKeyword(any(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun `searchKeyword - MCP active but host does not match - uses native delegate`() {
        val mcpRepo: McpServerConfigPort = mock()
        val delegate: GitlabPort = mock()
        val routed: GitlabPort = mock()
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(delegate.searchKeyword(any(), any(), any(), anyOrNull(), any())).thenReturn(emptyList())

        val adapter = buildAdapter(mcpRepo, delegate = delegate, routed = routed)
        adapter.searchKeyword(nonMatchingConfig(), "payment", 1L)

        verify(delegate).searchKeyword(any(), any(), any(), anyOrNull(), any())
        verify(routed, never()).searchKeyword(any(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun `searchKeyword - MCP not active - uses native delegate`() {
        val mcpRepo: McpServerConfigPort = mock()
        val delegate: GitlabPort = mock()
        val routed: GitlabPort = mock()
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(delegate.searchKeyword(any(), any(), any(), anyOrNull(), any())).thenReturn(emptyList())

        val adapter = buildAdapter(mcpRepo, delegate = delegate, routed = routed)
        adapter.searchKeyword(nonMatchingConfig(), "keyword", 1L)

        verify(delegate).searchKeyword(any(), any(), any(), anyOrNull(), any())
        verify(routed, never()).searchKeyword(any(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun `resolvePort - MCP gitlabBaseUrl with trailing slash matches config without`() {
        val mcpRepo: McpServerConfigPort = mock()
        val delegate: GitlabPort = mock()
        val routed: GitlabPort = mock()
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp("http://localhost:3080/"))
        whenever(routed.listAccessibleProjects(any())).thenReturn(emptyList())

        val adapter = buildAdapter(mcpRepo, delegate = delegate, routed = routed)
        adapter.listAccessibleProjects(matchingConfig())

        verify(routed).listAccessibleProjects(any())
        verify(delegate, never()).listAccessibleProjects(any())
    }

    @Test
    fun `resolvePort - URL matching is case-insensitive`() {
        val mcpRepo: McpServerConfigPort = mock()
        val delegate: GitlabPort = mock()
        val routed: GitlabPort = mock()
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp("HTTP://LOCALHOST:3080"))
        whenever(routed.listAccessibleProjects(any())).thenReturn(emptyList())

        val adapter = buildAdapter(mcpRepo, delegate = delegate, routed = routed)
        adapter.listAccessibleProjects(matchingConfig())

        verify(routed).listAccessibleProjects(any())
        verify(delegate, never()).listAccessibleProjects(any())
    }
}

class McpAwareIntegrationAdaptersMatchesFilterTest {
    private val mcpRepo: com.stacklens.application.port.out.McpServerConfigPort = mock()
    private val gateway: com.stacklens.application.port.out.McpGatewayPort = mock()
    private val om =
        com.fasterxml.jackson.databind
            .ObjectMapper()
    private val delegate: com.stacklens.application.port.out.search.GrafanaSearchPort = mock()

    private fun adapter() = McpAwareGrafanaSearchAdapter(mcpRepo, gateway, om, delegate)

    private fun method(methodName: String): java.lang.reflect.Method {
        val m = McpAwareGrafanaSearchAdapter::class.java.getDeclaredMethod(methodName, com.fasterxml.jackson.databind.JsonNode::class.java, List::class.java)
        m.isAccessible = true
        return m
    }

    private fun dash(
        url: String = "",
        title: String = "",
        uid: String = "",
    ): com.fasterxml.jackson.databind.JsonNode {
        val node = om.createObjectNode()
        node.put("url", url)
        node.put("title", title)
        node.put("uid", uid)
        return node
    }

    @Test
    fun `matchesFilter returns true when filters list is empty`() {
        val result = method("matchesFilter").invoke(adapter(), dash(), emptyList<String>()) as Boolean
        assertTrue(result)
    }

    @Test
    fun `matchesFilter matches when filter is contained in url`() {
        val result = method("matchesFilter").invoke(adapter(), dash(url = "/dashboards/foo"), listOf("foo")) as Boolean
        assertTrue(result)
    }

    @Test
    fun `matchesFilter matches when filter is contained in title`() {
        val result = method("matchesFilter").invoke(adapter(), dash(title = "Production Metrics"), listOf("production")) as Boolean
        assertTrue(result)
    }

    @Test
    fun `matchesFilter matches when filter equals uid exactly`() {
        val result = method("matchesFilter").invoke(adapter(), dash(uid = "abc123"), listOf("abc123")) as Boolean
        assertTrue(result)
    }

    @Test
    fun `matchesFilter matches when filter contains slash-uid-slash`() {
        val result = method("matchesFilter").invoke(adapter(), dash(uid = "abc123"), listOf("/dashboards/abc123/view")) as Boolean
        assertTrue(result)
    }

    @Test
    fun `matchesFilter matches when filter ends with slash-uid`() {
        val result = method("matchesFilter").invoke(adapter(), dash(uid = "abc123"), listOf("/dashboards/abc123")) as Boolean
        assertTrue(result)
    }

    @Test
    fun `matchesFilter trimmed branch matches when filter is wrapped in slashes and contains in url`() {
        val result = method("matchesFilter").invoke(adapter(), dash(url = "/dashboards/foo"), listOf("/foo/")) as Boolean
        assertTrue(result)
    }

    @Test
    fun `matchesFilter trimmed branch matches when filter is wrapped in slashes and contains in title`() {
        val result = method("matchesFilter").invoke(adapter(), dash(title = "foo metrics"), listOf("/foo/")) as Boolean
        assertTrue(result)
    }

    @Test
    fun `matchesFilter returns false when no branch matches`() {
        val result = method("matchesFilter").invoke(adapter(), dash(url = "/foo", title = "bar", uid = "baz"), listOf("nonexistent")) as Boolean
        assertFalse(result)
    }
}
