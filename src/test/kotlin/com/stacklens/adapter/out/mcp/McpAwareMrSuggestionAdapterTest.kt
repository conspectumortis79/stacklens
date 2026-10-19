package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.MrSuggestionPort
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class McpAwareMrSuggestionAdapterTest {
    private val mcpRepo = mock<McpServerConfigPort>()
    private val gateway = mock<McpGatewayPort>()
    private val delegate = mock<MrSuggestionPort>()
    private val mapper = ObjectMapper()
    private lateinit var adapter: McpAwareMrSuggestionAdapter

    private val mcpCfg =
        McpServerConfig(
            active = true,
            routeGitlab = true,
            routeGithub = true,
            gitlabAccessToken = "TOK",
            githubToken = "TOK",
        )

    private fun baseReq(
        url: String = "https://gitlab.com/grp/repo/-/merge_requests/42",
        filePath: String = "src/Foo.kt",
        lineNumber: Int = 12,
        suggestedCode: String = "println(\"hi\")",
    ) = MrSuggestionPort.PostRequest(
        mrUrl = url,
        platform = "GitLab",
        gitConfigId = null,
        gitlabConfigs = listOf(GitlabConfig(name = "g", baseUrl = "https://gitlab.com", accessToken = "TOK")),
        githubConfigs = listOf(GithubRepoConfig(name = "n", owner = "octo", repo = "cat", accessToken = "TOK")),
        diffBaseSha = "b",
        diffStartSha = "s",
        diffHeadSha = "h",
        filePath = filePath,
        lineNumber = lineNumber,
        suggestedCode = suggestedCode,
    )

    @BeforeEach
    fun setUp() {
        adapter = McpAwareMrSuggestionAdapter(mcpRepo, gateway, mapper, delegate)
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
    }

    @Test
    fun `postSuggestion falls back to delegate when no MCP routing`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(delegate.postSuggestion(any())).thenReturn("https://delegate/url")
        val result = adapter.postSuggestion(baseReq("not-a-url"))
        assertEquals("https://delegate/url", result)
    }

    @Test
    fun `postSuggestion routes GitLab suggestion via MCP`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"id":"d1","notes":[{"web_url":"https://gl/discussion/1"}]}"""),
        )
        val result = adapter.postSuggestion(baseReq())
        assertEquals("https://gl/discussion/1", result)
    }

    @Test
    fun `postSuggestion routes GitHub suggestion via MCP`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"html_url":"https://gh/comment/1"}"""),
        )
        val result = adapter.postSuggestion(baseReq("https://github.com/octo/cat/pull/9"))
        assertEquals("https://gh/comment/1", result)
    }

    @Test
    fun `postSuggestion routes GitHub Enterprise via MCP`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"html_url":"https://ghe/comment/1"}"""),
        )
        val result = adapter.postSuggestion(baseReq("https://api.github.com/octo/cat/pull/1"))
        assertEquals("https://ghe/comment/1", result)
    }

    @Test
    fun `postSuggestion falls back when MCP returns error on GitLab path`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(isError = true, text = "err"))
        whenever(delegate.postSuggestion(any())).thenReturn("https://delegate")
        val result = adapter.postSuggestion(baseReq())
        assertEquals("https://delegate", result)
    }

    @Test
    fun `postSuggestion falls back when MCP returns error on GitHub path`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(isError = true, text = "err"))
        whenever(delegate.postSuggestion(any())).thenReturn("https://delegate")
        val result = adapter.postSuggestion(baseReq("https://github.com/octo/cat/pull/9"))
        assertEquals("https://delegate", result)
    }

    @Test
    fun `postSuggestion falls back when MCP gateway throws on GitLab path`() {
        whenever(gateway.callTool(any(), any(), any())).doThrow(RuntimeException("boom"))
        whenever(delegate.postSuggestion(any())).thenReturn("https://delegate")
        val result = adapter.postSuggestion(baseReq())
        assertEquals("https://delegate", result)
    }

    @Test
    fun `postSuggestion falls back when GitLab URL has no MR iid`() {
        whenever(delegate.postSuggestion(any())).thenReturn("https://delegate")
        val result = adapter.postSuggestion(baseReq("https://gitlab.com/grp/repo/-/merge_requests/abc"))
        assertEquals("https://delegate", result)
    }

    @Test
    fun `postSuggestion falls back when GitHub URL has no PR number`() {
        whenever(delegate.postSuggestion(any())).thenReturn("https://delegate")
        val result = adapter.postSuggestion(baseReq("https://github.com/octo/cat/issues/1"))
        assertEquals("https://delegate", result)
    }

    @Test
    fun `postSuggestion falls back when GitHub URL path has fewer than 4 segments`() {
        whenever(delegate.postSuggestion(any())).thenReturn("https://delegate")
        val result = adapter.postSuggestion(baseReq("https://github.com/octo/cat"))
        assertEquals("https://delegate", result)
    }

    @Test
    fun `postSuggestion handles empty suggestedCode`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"notes":[{"web_url":"https://gl/x"}]}"""),
        )
        val result = adapter.postSuggestion(baseReq(suggestedCode = ""))
        assertEquals("https://gl/x", result)
    }

    @Test
    fun `postSuggestion falls back when GitLab response has no notes array`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"id":"d1"}"""),
        )
        val result = adapter.postSuggestion(baseReq("https://gitlab.example.com/grp/repo/-/merge_requests/42"))
        assertEquals("https://gitlab.example.com/grp/repo/-/merge_requests/42", result)
    }

    @Test
    fun `postSuggestion falls back when GitLab response notes has empty web_url`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"notes":[{"web_url":""}]}"""),
        )
        val result = adapter.postSuggestion(baseReq())
        assertEquals("https://gitlab.com/grp/repo/-/merge_requests/42", result)
    }

    @Test
    fun `postSuggestion falls back when GitHub response has no html_url`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"id":"c1"}"""),
        )
        val result = adapter.postSuggestion(baseReq("https://github.com/octo/cat/pull/9"))
        assertEquals("https://github.com/octo/cat/pull/9", result)
    }

    @Test
    fun `postSuggestion falls back when GitHub response html_url is blank`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"html_url":""}"""),
        )
        val result = adapter.postSuggestion(baseReq("https://github.com/octo/cat/pull/9"))
        assertEquals("https://github.com/octo/cat/pull/9", result)
    }

    @Test
    fun `postSuggestion falls back when GitHub URL has malformed PR number`() {
        whenever(delegate.postSuggestion(any())).thenReturn("https://delegate")
        val result = adapter.postSuggestion(baseReq("https://github.com/octo/cat/pull/notanumber"))
        assertEquals("https://delegate", result)
    }

    @Test
    fun `postSuggestion falls back when GitHub URL path is too short`() {
        whenever(delegate.postSuggestion(any())).thenReturn("https://delegate")
        val result = adapter.postSuggestion(baseReq("https://github.com/octo/cat"))
        assertEquals("https://delegate", result)
    }

    @Test
    fun `postSuggestion falls back when GitHub URL has wrong segment type`() {
        whenever(delegate.postSuggestion(any())).thenReturn("https://delegate")
        val result = adapter.postSuggestion(baseReq("https://github.com/octo/cat/issues/9"))
        assertEquals("https://delegate", result)
    }

    @Test
    fun `postSuggestion falls back when MCP gateway throws on GitHub path`() {
        whenever(gateway.callTool(any(), any(), any())).doThrow(RuntimeException("boom"))
        whenever(delegate.postSuggestion(any())).thenReturn("https://delegate")
        val result = adapter.postSuggestion(baseReq("https://github.com/octo/cat/pull/9"))
        assertEquals("https://delegate", result)
    }

    @Test
    fun `postSuggestion falls back when URL has no host`() {
        whenever(delegate.postSuggestion(any())).thenReturn("https://delegate")
        val result = adapter.postSuggestion(baseReq("not-a-url"))
        assertEquals("https://delegate", result)
    }

    @Test
    fun `postSuggestion handles GitLab URL with deeply nested project path`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"notes":[{"web_url":"https://gl/nested"}]}"""),
        )
        val result = adapter.postSuggestion(baseReq("https://gitlab.com/group/sub/repo/-/merge_requests/77"))
        assertEquals("https://gl/nested", result)
    }

    @Test
    fun `postSuggestion handles GitHub Enterprise with subdomain`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"html_url":"https://ghe/comment"}"""),
        )
        val result = adapter.postSuggestion(baseReq("https://myorg.github.com/octo/cat/pull/9"))
        assertEquals("https://ghe/comment", result)
    }

    @Test
    fun `postSuggestion uses diffStartSha fallback when blank for GitLab`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"notes":[{"web_url":"https://gl/x"}]}"""),
        )
        val req = baseReq().copy(diffStartSha = "")
        val result = adapter.postSuggestion(req)
        assertEquals("https://gl/x", result)
    }

    @Test
    fun `postSuggestion with non-github host routes to GitLab path`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"notes":[{"web_url":"https://gl/x"}]}"""),
        )
        val result = adapter.postSuggestion(baseReq("https://gitlab.example.com/grp/repo/-/merge_requests/42"))
        assertEquals("https://gl/x", result)
    }

    @Test
    fun `postSuggestion with custom platform host routes to GitLab path`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"notes":[{"web_url":"https://gl/x"}]}"""),
        )
        val result = adapter.postSuggestion(baseReq("https://bitbucket.org/grp/repo/-/merge_requests/42"))
        assertEquals("https://gl/x", result)
    }

    @Test
    fun `postSuggestion handles GitLab path when MCP gitlab routing not ready`() {
        val cfg =
            McpServerConfig(
                active = true,
                routeGitlab = false,
                routeGithub = true,
                gitlabAccessToken = "TOK",
                githubToken = "TOK",
            )
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(cfg)
        whenever(delegate.postSuggestion(any())).thenReturn("https://delegate")
        val result = adapter.postSuggestion(baseReq())
        assertEquals("https://delegate", result)
    }

    @Test
    fun `postSuggestion handles GitHub path when MCP github routing not ready`() {
        val cfg =
            McpServerConfig(
                active = true,
                routeGitlab = true,
                routeGithub = false,
                gitlabAccessToken = "TOK",
                githubToken = "TOK",
            )
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(cfg)
        whenever(delegate.postSuggestion(any())).thenReturn("https://delegate")
        val result = adapter.postSuggestion(baseReq("https://github.com/octo/cat/pull/9"))
        assertEquals("https://delegate", result)
    }

    @Test
    fun `postSuggestion handles suggestedCode with trailing newlines`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"notes":[{"web_url":"https://gl/x"}]}"""),
        )
        val result = adapter.postSuggestion(baseReq(suggestedCode = "code\n\n\n"))
        assertEquals("https://gl/x", result)
    }

    @Test
    fun `postSuggestion falls back when GitLab URL has trailing non-numeric segment`() {
        whenever(delegate.postSuggestion(any())).thenReturn("https://delegate")
        val result = adapter.postSuggestion(baseReq("https://gitlab.com/grp/repo/-/merge_requests/42/diffs"))
        assertEquals("https://delegate", result)
    }

    @Test
    fun `postSuggestion handles GitHub URL with diff suffix`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"html_url":"https://gh/x"}"""),
        )
        val result = adapter.postSuggestion(baseReq("https://github.com/octo/cat/pull/9/files"))
        assertEquals("https://gh/x", result)
    }

    @Test
    fun `postSuggestion handles GitHub URL with fragment identifier`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"html_url":"https://gh/x"}"""),
        )
        val result = adapter.postSuggestion(baseReq("https://github.com/octo/cat/pull/9#discussion_r1"))
        assertEquals("https://gh/x", result)
    }

    @Test
    fun `postGitlab returns null when uri path is empty`() {
        whenever(delegate.postSuggestion(any())).thenReturn("https://delegate")
        val result = adapter.postSuggestion(baseReq("https://gitlab.example.com"))
        assertEquals("https://delegate", result)
    }
}
