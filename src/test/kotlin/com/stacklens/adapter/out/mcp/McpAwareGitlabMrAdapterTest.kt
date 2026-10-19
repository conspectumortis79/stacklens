package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.mr.GitlabMrPort
import com.stacklens.application.port.out.mr.GitlabMrProjectDetail
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class McpAwareGitlabMrAdapterTest {
    private val mcpRepo = mock<McpServerConfigPort>()
    private val gateway = mock<McpGatewayPort>()
    private val delegate = mock<GitlabMrPort>()
    private val mapper = ObjectMapper()
    private lateinit var adapter: McpAwareGitlabMrAdapter

    private val cfg = GitlabConfig(name = "g", baseUrl = "https://gl", accessToken = "TOK", active = true)
    private val mcpCfg =
        McpServerConfig(
            active = true,
            routeGitlab = true,
            gitlabAccessToken = "TOK",
        )

    @BeforeEach
    fun setUp() {
        adapter = McpAwareGitlabMrAdapter(mcpRepo, gateway, mapper, delegate)
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
    }

    @Test
    fun `fetchProjectDetail falls back to delegate when no MCP routing`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        val detail = GitlabMrProjectDetail(id = 1L, name = "repo", defaultBranch = "main", pathWithNamespace = "g/r", webUrl = "u")
        whenever(delegate.fetchProjectDetail(cfg, 1L)).thenReturn(detail)
        val result = adapter.fetchProjectDetail(cfg, 1L)
        assertEquals(detail, result)
        verify(gateway, never()).callTool(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun `fetchProjectDetail routes via MCP and parses JSON`() {
        whenever(gateway.callTool(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """{"id":42,"name":"repo","default_branch":"develop","path_with_namespace":"grp/repo","web_url":"https://gl/grp/repo"}""",
            ),
        )
        val result = adapter.fetchProjectDetail(cfg, 42L)
        assertEquals(42L, result.id)
        assertEquals("repo", result.name)
        assertEquals("develop", result.defaultBranch)
        assertEquals("grp/repo", result.pathWithNamespace)
        assertEquals("https://gl/grp/repo", result.webUrl)
    }

    @Test
    fun `fetchProjectDetail uses main as default branch fallback`() {
        whenever(gateway.callTool(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(
            McpToolResult(isError = false, text = """{"id":1,"name":"x","path_with_namespace":"x/x","web_url":"u"}"""),
        )
        val result = adapter.fetchProjectDetail(cfg, 1L)
        assertEquals("main", result.defaultBranch)
    }

    @Test
    fun `fetchProjectDetail falls back when MCP returns error`() {
        whenever(gateway.callTool(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(
            McpToolResult(isError = true, text = "upstream failed"),
        )
        val fallback = GitlabMrProjectDetail(id = 99L, name = "fb", defaultBranch = "main", pathWithNamespace = "g/fb", webUrl = "u")
        whenever(delegate.fetchProjectDetail(cfg, 99L)).thenReturn(fallback)
        val result = adapter.fetchProjectDetail(cfg, 99L)
        assertEquals(fallback, result)
    }

    @Test
    fun `fetchProjectDetail falls back when MCP returns invalid JSON`() {
        whenever(gateway.callTool(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(
            McpToolResult(isError = false, text = "not json"),
        )
        val fallback = GitlabMrProjectDetail(id = 7L, name = "fb", defaultBranch = "main", pathWithNamespace = "g/fb", webUrl = "u")
        whenever(delegate.fetchProjectDetail(cfg, 7L)).thenReturn(fallback)
        val result = adapter.fetchProjectDetail(cfg, 7L)
        assertEquals(fallback, result)
    }

    @Test
    fun `fetchProjectDetail falls back when MCP gateway throws`() {
        whenever(gateway.callTool(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .doThrow(RuntimeException("boom"))
        val fallback = GitlabMrProjectDetail(id = 5L, name = "fb", defaultBranch = "main", pathWithNamespace = "g/fb", webUrl = "u")
        whenever(delegate.fetchProjectDetail(cfg, 5L)).thenReturn(fallback)
        val result = adapter.fetchProjectDetail(cfg, 5L)
        assertEquals(fallback, result)
    }

    @Test
    fun `createBranch falls back to delegate when no MCP routing`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        adapter.createBranch(cfg, 1L, "feat", "main")
        verify(delegate).createBranch(cfg, 1L, "feat", "main")
        verify(gateway, never()).callTool(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun `createBranch routes via MCP and does not delegate on success`() {
        whenever(gateway.callTool(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(
            McpToolResult(isError = false, text = "ok"),
        )
        adapter.createBranch(cfg, 1L, "feat", "main")
        verify(
            delegate,
            never(),
        ).createBranch(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun `createBranch falls back to delegate when MCP returns error`() {
        whenever(gateway.callTool(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(
            McpToolResult(isError = true, text = "MCP circuit open for 39:gitlab"),
        )
        adapter.createBranch(cfg, 1L, "feat", "main")
        verify(delegate).createBranch(cfg, 1L, "feat", "main")
    }

    @Test
    fun `createBranch falls back when MCP gateway throws`() {
        whenever(gateway.callTool(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .doThrow(RuntimeException("boom"))
        adapter.createBranch(cfg, 1L, "feat", "main")
        verify(delegate).createBranch(cfg, 1L, "feat", "main")
    }

    @Test
    fun `commitFiles falls back to delegate when no MCP routing`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        val actions = listOf(mapOf("action" to "create", "filePath" to "x", "content" to "y"))
        adapter.commitFiles(cfg, 1L, "feat", "msg", actions)
        verify(delegate).commitFiles(cfg, 1L, "feat", "msg", actions)
        verify(gateway, never()).callTool(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun `commitFiles routes via MCP and does not delegate on success`() {
        whenever(gateway.callTool(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(
            McpToolResult(isError = false, text = "ok"),
        )
        adapter.commitFiles(cfg, 1L, "feat", "msg", emptyList())
        verify(
            delegate,
            never(),
        ).commitFiles(
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
        )
    }

    @Test
    fun `commitFiles falls back when MCP returns error`() {
        whenever(gateway.callTool(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(
            McpToolResult(isError = true, text = "upstream failed"),
        )
        val actions = listOf(mapOf("action" to "create"))
        adapter.commitFiles(cfg, 1L, "feat", "msg", actions)
        verify(delegate).commitFiles(cfg, 1L, "feat", "msg", actions)
    }

    @Test
    fun `commitFiles falls back when MCP gateway throws`() {
        whenever(gateway.callTool(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .doThrow(RuntimeException("boom"))
        val actions = listOf(mapOf("action" to "create"))
        adapter.commitFiles(cfg, 1L, "feat", "msg", actions)
        verify(delegate).commitFiles(cfg, 1L, "feat", "msg", actions)
    }

    @Test
    fun `createMergeRequest falls back to delegate when no MCP routing`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(delegate.createMergeRequest(cfg, 1L, "feat", "main", "T", "D")).thenReturn("https://gl/mr/1")
        val url = adapter.createMergeRequest(cfg, 1L, "feat", "main", "T", "D")
        assertEquals("https://gl/mr/1", url)
        verify(gateway, never()).callTool(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun `createMergeRequest routes via MCP and returns web_url`() {
        whenever(gateway.callTool(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(
            McpToolResult(isError = false, text = """{"web_url":"https://gl/grp/repo/-/merge_requests/42"}"""),
        )
        val url = adapter.createMergeRequest(cfg, 1L, "feat", "main", "T", "D")
        assertEquals("https://gl/grp/repo/-/merge_requests/42", url)
    }

    @Test
    fun `createMergeRequest falls back when MCP returns error`() {
        whenever(gateway.callTool(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(
            McpToolResult(isError = true, text = "upstream failed"),
        )
        whenever(delegate.createMergeRequest(cfg, 1L, "feat", "main", "T", "D")).thenReturn("https://delegate/mr")
        val url = adapter.createMergeRequest(cfg, 1L, "feat", "main", "T", "D")
        assertEquals("https://delegate/mr", url)
    }

    @Test
    fun `createMergeRequest falls back when MCP returns invalid JSON`() {
        whenever(gateway.callTool(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(
            McpToolResult(isError = false, text = "not json"),
        )
        whenever(delegate.createMergeRequest(cfg, 1L, "feat", "main", "T", "D")).thenReturn("https://delegate/mr")
        val url = adapter.createMergeRequest(cfg, 1L, "feat", "main", "T", "D")
        assertEquals("https://delegate/mr", url)
    }

    @Test
    fun `createMergeRequest falls back when MCP returns empty web_url`() {
        whenever(gateway.callTool(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(
            McpToolResult(isError = false, text = """{"web_url":""}"""),
        )
        whenever(delegate.createMergeRequest(cfg, 1L, "feat", "main", "T", "D")).thenReturn("https://delegate/mr")
        val url = adapter.createMergeRequest(cfg, 1L, "feat", "main", "T", "D")
        assertEquals("https://delegate/mr", url)
    }

    @Test
    fun `createMergeRequest falls back when MCP gateway throws`() {
        whenever(gateway.callTool(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .doThrow(RuntimeException("boom"))
        whenever(delegate.createMergeRequest(cfg, 1L, "feat", "main", "T", "D")).thenReturn("https://delegate/mr")
        val url = adapter.createMergeRequest(cfg, 1L, "feat", "main", "T", "D")
        assertEquals("https://delegate/mr", url)
    }
}
