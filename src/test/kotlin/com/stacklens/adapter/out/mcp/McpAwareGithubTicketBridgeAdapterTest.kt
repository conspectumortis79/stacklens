package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.port.out.JiraCodeFile
import com.stacklens.application.port.out.JiraCodeImplementation
import com.stacklens.application.port.out.JiraDatabaseChange
import com.stacklens.application.port.out.JiraRepoContext
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.domain.enums.FileChangeType
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path

class McpAwareGithubTicketBridgeAdapterTest {
    private val mcpRepo = mock<McpServerConfigPort>()
    private val gateway = mock<McpGatewayPort>()
    private val delegate = mock<GithubTicketBridgePort>()
    private val mapper = ObjectMapper()
    private lateinit var adapter: McpAwareGithubTicketBridgeAdapter

    private val cfg = GithubRepoConfig(id = 1L, name = "n", owner = "octo", repo = "cat", accessToken = "TOK")
    private val cfgWithBranch = GithubRepoConfig(id = 1L, name = "n", owner = "octo", repo = "cat", accessToken = "TOK", branch = "develop")
    private val mcpCfg =
        McpServerConfig(
            active = true,
            routeGithub = true,
            githubToken = "TOK",
        )

    private val fallbackRepoCtx = JiraRepoContext(repoName = "octo/cat", projectId = 1L, files = emptyList())
    private val ticket =
        JiraTicketData(
            key = "P-1",
            summary = "S",
            description = "D",
            comments = "",
            issueType = "Bug",
            status = "Open",
            priority = null,
            labels = emptyList(),
            components = emptyList(),
            url = "u",
        )

    private fun emptyImpl() =
        JiraCodeImplementation(
            identifiedRepositories = emptyList(),
            identifiedDatabases = emptyList(),
            files = emptyList(),
            testFiles = emptyList(),
            systemTestFiles = emptyList(),
            databaseChanges = emptyList<JiraDatabaseChange>(),
            implementationNotes = "",
        )

    private fun implWithFiles(files: List<JiraCodeFile>) =
        JiraCodeImplementation(
            identifiedRepositories = emptyList(),
            identifiedDatabases = emptyList(),
            files = files,
            testFiles = emptyList(),
            systemTestFiles = emptyList(),
            databaseChanges = emptyList<JiraDatabaseChange>(),
            implementationNotes = "",
        )

    private fun createFile(
        path: String,
        code: String,
    ) = JiraCodeFile(
        repository = "octo/cat",
        path = path,
        language = "kotlin",
        description = "",
        changeType = FileChangeType.CREATE,
        code = code,
        patches = emptyList(),
    )

    @BeforeEach
    fun setUp() {
        adapter = McpAwareGithubTicketBridgeAdapter(mcpRepo, gateway, mapper, delegate)
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
    }

    @Test
    fun `fetchRepoContext falls back when no MCP routing`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(delegate.fetchRepoContext(cfg)).thenReturn(fallbackRepoCtx)
        val result = adapter.fetchRepoContext(cfg)
        assertEquals(fallbackRepoCtx, result)
    }

    @Test
    fun `fetchRepoContext returns empty context when MCP returns invalid JSON`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(McpToolResult(isError = false, text = """{"default_branch":"main"}"""))
            .thenReturn(McpToolResult(isError = false, text = "not json"))
        whenever(delegate.fetchRepoContext(cfg)).thenReturn(fallbackRepoCtx)
        val result = adapter.fetchRepoContext(cfg)
        assertEquals(fallbackRepoCtx, result)
    }

    @Test
    fun `createPullRequest throws when MCP returns no html_url`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(McpToolResult(isError = false, text = """{"object":{"sha":"abc"}}"""))
            .thenReturn(McpToolResult(isError = false, text = """{"ref":"refs/heads/feat"}"""))
            .thenReturn(McpToolResult(isError = false, text = """{"sha":null}"""))
            .thenReturn(McpToolResult(isError = false, text = """{"number":42}"""))
        whenever(delegate.createPullRequest(eq(cfg), eq(ticket), any())).thenReturn("https://github.com/fallback")
        val result = adapter.createPullRequest(cfg, ticket, emptyImpl())
        assertEquals("https://github.com/fallback", result)
    }

    @Test
    fun `createPullRequest throws when get_ref returns no sha`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(McpToolResult(isError = false, text = """{"object":{}}"""))
        whenever(delegate.createPullRequest(eq(cfg), eq(ticket), any())).thenReturn("https://github.com/fallback")
        val result = adapter.createPullRequest(cfg, ticket, emptyImpl())
        assertEquals("https://github.com/fallback", result)
    }

    @Test
    fun `fetchRepoContext routes via MCP and parses file list`() {
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(McpToolResult(isError = false, text = """{"default_branch":"main"}"""))
            .thenReturn(
                McpToolResult(
                    isError = false,
                    text = """{"files":["src/Foo.kt","src/Bar.kt"],"contents":{}}""",
                ),
            )
        val result = adapter.fetchRepoContext(cfg)
        assertNotNull(result)
        assertEquals("octo/cat", result!!.repoName)
    }

    @Test
    fun `fetchRepoContext falls back when MCP returns error`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(isError = true, text = "err"))
        whenever(delegate.fetchRepoContext(cfg)).thenReturn(fallbackRepoCtx)
        val result = adapter.fetchRepoContext(cfg)
        assertEquals(fallbackRepoCtx, result)
    }

    @Test
    fun `fetchRepoContext falls back when MCP gateway throws`() {
        whenever(gateway.callTool(any(), any(), any())).doThrow(RuntimeException("boom"))
        whenever(delegate.fetchRepoContext(cfg)).thenReturn(fallbackRepoCtx)
        val result = adapter.fetchRepoContext(cfg)
        assertEquals(fallbackRepoCtx, result)
    }

    @Test
    fun `fetchRepoContext uses config branch when set`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"files":[],"contents":{}}"""),
        )
        val result = adapter.fetchRepoContext(cfgWithBranch)
        assertNotNull(result)
    }

    @Test
    fun `createPullRequest falls back when no MCP routing`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(delegate.createPullRequest(cfg, ticket, emptyImpl())).thenReturn("https://gh/pr/1")
        val result = adapter.createPullRequest(cfg, ticket, emptyImpl())
        assertEquals("https://gh/pr/1", result)
    }

    @Test
    fun `createPullRequest routes via MCP for empty implementation`() {
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(McpToolResult(isError = false, text = """{"default_branch":"main"}"""))
            .thenReturn(McpToolResult(isError = false, text = """{"object":{"sha":"baseSha123"}}"""))
            .thenReturn(McpToolResult(isError = false, text = """{}"""))
            .thenReturn(McpToolResult(isError = false, text = """{"html_url":"https://gh/pr/42"}"""))
        val result = adapter.createPullRequest(cfg, ticket, emptyImpl())
        assertEquals("https://gh/pr/42", result)
    }

    @Test
    fun `createPullRequest falls back when MCP returns error`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(isError = true, text = "err"))
        whenever(delegate.createPullRequest(cfg, ticket, emptyImpl())).thenReturn("https://delegate/pr/1")
        val result = adapter.createPullRequest(cfg, ticket, emptyImpl())
        assertEquals("https://delegate/pr/1", result)
    }

    @Test
    fun `createPullRequest falls back when MCP gateway throws`() {
        whenever(gateway.callTool(any(), any(), any())).doThrow(RuntimeException("boom"))
        whenever(delegate.createPullRequest(cfg, ticket, emptyImpl())).thenReturn("https://delegate/pr/1")
        val result = adapter.createPullRequest(cfg, ticket, emptyImpl())
        assertEquals("https://delegate/pr/1", result)
    }

    @Test
    fun `createPullRequest creates files with CREATE changeType`() {
        val file = createFile("/src/New.kt", "class New")
        val impl = implWithFiles(listOf(file))
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(McpToolResult(isError = false, text = """{"default_branch":"main"}"""))
            .thenReturn(McpToolResult(isError = false, text = """{"object":{"sha":"baseSha123"}}"""))
            .thenReturn(McpToolResult(isError = false, text = """{}"""))
            .thenReturn(McpToolResult(isError = false, text = """{"sha":null}"""))
            .thenReturn(McpToolResult(isError = false, text = """{}"""))
            .thenReturn(McpToolResult(isError = false, text = """{"html_url":"https://gh/pr/99"}"""))
        val result = adapter.createPullRequest(cfg, ticket, impl)
        assertEquals("https://gh/pr/99", result)
    }

    @Test
    fun `createPullRequest uses config branch when set`() {
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(McpToolResult(isError = false, text = """{"object":{"sha":"baseSha"}}"""))
            .thenReturn(McpToolResult(isError = false, text = """{}"""))
            .thenReturn(McpToolResult(isError = false, text = """{"html_url":"https://gh/pr/100"}"""))
        val result = adapter.createPullRequest(cfgWithBranch, ticket, emptyImpl())
        assertEquals("https://gh/pr/100", result)
    }

    @Test
    fun `detectGithubRemote delegates`() {
        val path = Path.of("/tmp/repo")
        val cfg2 = GithubRepoConfig(name = "n", owner = "octo", repo = "cat")
        whenever(delegate.detectGithubRemote(path)).thenReturn(cfg2)
        assertEquals(cfg2, adapter.detectGithubRemote(path))
    }

    @Test
    fun `detectGithubRemote returns null when delegate returns null`() {
        val path = Path.of("/tmp/none")
        whenever(delegate.detectGithubRemote(path)).thenReturn(null)
        assertNull(adapter.detectGithubRemote(path))
    }

    @Test
    fun `createPullRequest handles PATCH change type with patches`() {
        val patchedFile =
            JiraCodeFile(
                repository = "octo/cat",
                path = "src/A.kt",
                language = "kotlin",
                description = "",
                changeType = FileChangeType.PATCH,
                code = "",
                patches =
                    listOf(
                        com.stacklens.application.port.out.JiraCodePatch(
                            search = "old",
                            replace = "new",
                        ),
                    ),
            )
        val impl =
            JiraCodeImplementation(
                identifiedRepositories = emptyList(),
                identifiedDatabases = emptyList(),
                files = listOf(patchedFile),
                testFiles = emptyList(),
                systemTestFiles = emptyList(),
                databaseChanges = emptyList<JiraDatabaseChange>(),
                implementationNotes = "",
            )
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(McpToolResult(isError = false, text = """{"default_branch":"main"}"""))
            .thenReturn(McpToolResult(isError = false, text = """{"object":{"sha":"baseSha123"}}"""))
            .thenReturn(McpToolResult(isError = false, text = """{}"""))
            .thenReturn(McpToolResult(isError = false, text = """{"sha":"existing123"}"""))
            .thenReturn(McpToolResult(isError = false, text = """{"content":"old"}"""))
            .thenReturn(McpToolResult(isError = false, text = """{}"""))
            .thenReturn(McpToolResult(isError = false, text = """{"html_url":"https://gh/pr/patch"}"""))
        val result = adapter.createPullRequest(cfg, ticket, impl)
        assertEquals("https://gh/pr/patch", result)
    }

    @Test
    fun `createPullRequest falls back when create_ref returns error`() {
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(McpToolResult(isError = false, text = """{"default_branch":"main"}"""))
            .thenReturn(McpToolResult(isError = false, text = """{"object":{"sha":"abc"}}"""))
            .thenReturn(McpToolResult(isError = true, text = "create_ref failed"))
        whenever(delegate.createPullRequest(eq(cfg), eq(ticket), any())).thenReturn("https://delegate/1")
        val result = adapter.createPullRequest(cfg, ticket, emptyImpl())
        assertEquals("https://delegate/1", result)
    }

    @Test
    fun `createPullRequest falls back when put_content returns error`() {
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(McpToolResult(isError = false, text = """{"default_branch":"main"}"""))
            .thenReturn(McpToolResult(isError = false, text = """{"object":{"sha":"abc"}}"""))
            .thenReturn(McpToolResult(isError = false, text = """{}"""))
            .thenReturn(McpToolResult(isError = false, text = """{"sha":null}"""))
            .thenReturn(McpToolResult(isError = true, text = "put_content failed"))
        whenever(delegate.createPullRequest(eq(cfg), eq(ticket), any())).thenReturn("https://delegate/2")
        val result = adapter.createPullRequest(cfg, ticket, emptyImpl())
        assertEquals("https://delegate/2", result)
    }

    @Test
    fun `createPullRequest falls back when create_pull_request returns error`() {
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(McpToolResult(isError = false, text = """{"default_branch":"main"}"""))
            .thenReturn(McpToolResult(isError = false, text = """{"object":{"sha":"abc"}}"""))
            .thenReturn(McpToolResult(isError = false, text = """{}"""))
            .thenReturn(McpToolResult(isError = false, text = """{"sha":null}"""))
            .thenReturn(McpToolResult(isError = false, text = """{}"""))
            .thenReturn(McpToolResult(isError = true, text = "create_pr failed"))
        whenever(delegate.createPullRequest(eq(cfg), eq(ticket), any())).thenReturn("https://delegate/3")
        val result = adapter.createPullRequest(cfg, ticket, emptyImpl())
        assertEquals("https://delegate/3", result)
    }
}
