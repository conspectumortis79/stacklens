package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class McpAwareMrFetchAdapterTest {
    private val mcpRepo = mock<McpServerConfigPort>()
    private val gateway = mock<McpGatewayPort>()
    private val delegate = mock<MrFetchPort>()
    private val mapper = ObjectMapper()
    private lateinit var adapter: McpAwareMrFetchAdapter

    private val gitlabCfg = GitlabConfig(name = "g", baseUrl = "https://gl", accessToken = "TOK")
    private val githubCfg = GithubRepoConfig(name = "n", owner = "octo", repo = "cat", accessToken = "TOK")

    private val gitlabMcpCfg =
        McpServerConfig(
            active = true,
            routeGitlab = true,
            gitlabAccessToken = "TOK",
        )

    private val githubMcpCfg =
        McpServerConfig(
            active = true,
            routeGithub = true,
            githubToken = "TOK",
        )

    @BeforeEach
    fun setUp() {
        adapter = McpAwareMrFetchAdapter(mcpRepo, gateway, mapper, delegate)
    }

    @Test
    fun `fetchMrData delegates when no MCP routing`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        val expected = MrFetchPort.MrFetchResult.Failure("not found")
        whenever(delegate.fetchMrData("https://gl/g/r/-/merge_requests/1", listOf(gitlabCfg), emptyList())).thenReturn(expected)
        val result = adapter.fetchMrData("https://gl/g/r/-/merge_requests/1", listOf(gitlabCfg), emptyList())
        assertEquals(expected, result)
        verify(gateway, never()).callTool(any(), any(), any())
    }

    @Test
    fun `fetchMrData delegates for unknown URL`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(gitlabMcpCfg)
        val expected = MrFetchPort.MrFetchResult.Failure("not recognized")
        whenever(delegate.fetchMrData("not-a-url", emptyList(), emptyList())).thenReturn(expected)
        val result = adapter.fetchMrData("not-a-url", emptyList(), emptyList())
        assertEquals(expected, result)
    }

    @Test
    fun `fetchMrData routes GitLab via MCP with non-empty changes`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(gitlabMcpCfg)
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(
                McpToolResult(
                    isError = false,
                    text = """{"iid":"1","title":"MR Title","description":"desc","source_branch":"feat","target_branch":"main","author":{"name":"alice"},"state":"opened"}""",
                ),
            ).thenReturn(
                McpToolResult(
                    isError = false,
                    text = """{"changes":[
                        {"new_path":"a.txt","old_path":"a_old.txt","diff":"@@ -1 +1 @@\n-old\n+new"},
                        {"new_path":"","old_path":"b.txt","diff":"@@ -1 +1 @@\n-old\n+new"},
                        {"new_path":"","old_path":"","diff":""}
                    ]}""",
                ),
            )
        val result = adapter.fetchMrData("https://gl/g/r/-/merge_requests/1", listOf(gitlabCfg), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Success)
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertTrue(data.changedFiles.contains("a.txt"))
        assertTrue(data.changedFiles.contains("b.txt"))
    }

    @Test
    fun `fetchMrData routes GitLab via MCP`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(gitlabMcpCfg)
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(
                McpToolResult(
                    isError = false,
                    text = """{"iid":"1","title":"MR Title","description":"desc","source_branch":"feat","target_branch":"main","author":{"name":"alice"},"state":"opened"}""",
                ),
            ).thenReturn(McpToolResult(isError = false, text = """{"changes":[]}"""))
        val result = adapter.fetchMrData("https://gl/g/r/-/merge_requests/1", listOf(gitlabCfg), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Success)
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertEquals("MR Title", data.title)
    }

    @Test
    fun `fetchMrData falls back to delegate when GitLab MCP returns error`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(gitlabMcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(isError = true, text = "err"))
        val expected = MrFetchPort.MrFetchResult.Failure("fallback")
        whenever(delegate.fetchMrData("https://gl/g/r/-/merge_requests/1", listOf(gitlabCfg), emptyList())).thenReturn(expected)
        val result = adapter.fetchMrData("https://gl/g/r/-/merge_requests/1", listOf(gitlabCfg), emptyList())
        assertEquals(expected, result)
    }

    @Test
    fun `fetchMrData falls back to delegate when GitLab MCP returns invalid JSON`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(gitlabMcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = "not json"),
        )
        val expected = MrFetchPort.MrFetchResult.Failure("fallback")
        whenever(delegate.fetchMrData("https://gl/g/r/-/merge_requests/1", listOf(gitlabCfg), emptyList())).thenReturn(expected)
        val result = adapter.fetchMrData("https://gl/g/r/-/merge_requests/1", listOf(gitlabCfg), emptyList())
        assertEquals(expected, result)
    }

    @Test
    fun `fetchMrData falls back to delegate when GitLab MCP gateway throws`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(gitlabMcpCfg)
        whenever(gateway.callTool(any(), any(), any())).doThrow(RuntimeException("boom"))
        val expected = MrFetchPort.MrFetchResult.Failure("fallback")
        whenever(delegate.fetchMrData("https://gl/g/r/-/merge_requests/1", listOf(gitlabCfg), emptyList())).thenReturn(expected)
        val result = adapter.fetchMrData("https://gl/g/r/-/merge_requests/1", listOf(gitlabCfg), emptyList())
        assertEquals(expected, result)
    }

    @Test
    fun `fetchMrData returns Success when GitLab changes MCP returns error`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(gitlabMcpCfg)
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(
                McpToolResult(
                    isError = false,
                    text = """{"iid":"1","title":"T","description":"D","source_branch":"feat","target_branch":"main","author":{"name":"alice"},"state":"opened"}""",
                ),
            ).thenReturn(McpToolResult(isError = true, text = "err"))
        val result = adapter.fetchMrData("https://gl/g/r/-/merge_requests/1", listOf(gitlabCfg), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Success)
    }

    @Test
    fun `fetchMrData routes GitHub via MCP`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(githubMcpCfg)
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(
                McpToolResult(
                    isError = false,
                    text = """{"number":9,"title":"PR Title","body":"desc","head":{"ref":"feat"},"base":{"ref":"main"},"user":{"login":"octocat"},"state":"open"}""",
                ),
            ).thenReturn(McpToolResult(isError = false, text = """[]"""))
        val result = adapter.fetchMrData("https://github.com/octo/cat/pull/9", emptyList(), listOf(githubCfg))
        assertTrue(result is MrFetchPort.MrFetchResult.Success)
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertEquals("PR Title", data.title)
    }

    @Test
    fun `fetchMrData falls back when GitHub MCP returns error`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(githubMcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(isError = true, text = "err"))
        val expected = MrFetchPort.MrFetchResult.Failure("fallback")
        whenever(delegate.fetchMrData("https://github.com/octo/cat/pull/9", emptyList(), listOf(githubCfg))).thenReturn(expected)
        val result = adapter.fetchMrData("https://github.com/octo/cat/pull/9", emptyList(), listOf(githubCfg))
        assertEquals(expected, result)
    }

    @Test
    fun `fetchMrData falls back when GitHub MCP returns invalid JSON`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(githubMcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = "not json"),
        )
        val expected = MrFetchPort.MrFetchResult.Failure("fallback")
        whenever(delegate.fetchMrData("https://github.com/octo/cat/pull/9", emptyList(), listOf(githubCfg))).thenReturn(expected)
        val result = adapter.fetchMrData("https://github.com/octo/cat/pull/9", emptyList(), listOf(githubCfg))
        assertEquals(expected, result)
    }

    @Test
    fun `fetchMrData falls back when GitHub MCP gateway throws`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(githubMcpCfg)
        whenever(gateway.callTool(any(), any(), any())).doThrow(RuntimeException("boom"))
        val expected = MrFetchPort.MrFetchResult.Failure("fallback")
        whenever(delegate.fetchMrData("https://github.com/octo/cat/pull/9", emptyList(), listOf(githubCfg))).thenReturn(expected)
        val result = adapter.fetchMrData("https://github.com/octo/cat/pull/9", emptyList(), listOf(githubCfg))
        assertEquals(expected, result)
    }

    @Test
    fun `fetchMrData delegates when GitHub MCP is not configured`() {
        val inactiveGithubCfg =
            McpServerConfig(
                active = true,
                routeGithub = false,
                githubToken = "TOK",
            )
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(inactiveGithubCfg)
        val expected = MrFetchPort.MrFetchResult.Failure("not configured")
        whenever(delegate.fetchMrData("https://github.com/octo/cat/pull/9", emptyList(), listOf(githubCfg))).thenReturn(expected)
        val result = adapter.fetchMrData("https://github.com/octo/cat/pull/9", emptyList(), listOf(githubCfg))
        assertEquals(expected, result)
    }

    @Test
    fun `fetchMrData delegates when GitHub MCP token is blank`() {
        val blankTokenCfg =
            McpServerConfig(
                active = true,
                routeGithub = true,
                githubToken = "",
            )
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(blankTokenCfg)
        val expected = MrFetchPort.MrFetchResult.Failure("not configured")
        whenever(delegate.fetchMrData("https://github.com/octo/cat/pull/9", emptyList(), listOf(githubCfg))).thenReturn(expected)
        val result = adapter.fetchMrData("https://github.com/octo/cat/pull/9", emptyList(), listOf(githubCfg))
        assertEquals(expected, result)
    }

    @Test
    fun `fetchMrData returns Success when GitHub files MCP returns error`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(githubMcpCfg)
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(
                McpToolResult(
                    isError = false,
                    text = """{"number":9,"title":"T","body":"D","head":{"ref":"feat"},"base":{"ref":"main"},"user":{"login":"octocat"},"state":"open"}""",
                ),
            ).thenReturn(McpToolResult(isError = true, text = "err"))
        val result = adapter.fetchMrData("https://github.com/octo/cat/pull/9", emptyList(), listOf(githubCfg))
        assertTrue(result is MrFetchPort.MrFetchResult.Success)
    }
}
