package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.GitlabProjectInfo
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.McpServerConfig
import com.stacklens.domain.model.SearchResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClient
import java.util.function.Function

class McpAwareGitlabAdapterTest {
    private val mcpRepo: McpServerConfigPort = mock()
    private val gateway: McpGatewayPort = mock()
    private val delegate: GitlabPort = mock()
    private val routed: GitlabPort = mock()
    private val portFactory: Function<WebClient.Builder, GitlabPort> = mock()
    private val mapper = ObjectMapper()

    private lateinit var adapter: McpAwareGitlabAdapter

    private fun mcpCfg(
        active: Boolean = true,
        routeGitlab: Boolean = true,
        gitlabBaseUrl: String = "https://gitlab.example.com",
        gitlabAccessToken: String = "TOK",
    ) = McpServerConfig(
        active = active,
        routeGitlab = routeGitlab,
        gitlabBaseUrl = gitlabBaseUrl,
        gitlabAccessToken = gitlabAccessToken,
    )

    private fun cfg(baseUrl: String = "https://gitlab.example.com") =
        GitlabConfig(
            name = "test",
            baseUrl = baseUrl,
            accessToken = "TOK",
        )

    @BeforeEach
    fun setUp() {
        whenever(portFactory.apply(any())).thenReturn(routed)
        adapter =
            McpAwareGitlabAdapter(
                mcpRepo = mcpRepo,
                gateway = gateway,
                objectMapper = mapper,
                delegate = delegate,
                portFactory = portFactory,
                processControl = mock(),
            )
    }

    @Test
    fun `fetchProjectFiles delegates when no MCP active`() {
        whenever(delegate.fetchProjectFiles(any(), any(), any(), any())).thenReturn(emptyList())
        val result = adapter.fetchProjectFiles(cfg(), 1L, 10, 1024)
        assertEquals(0, result.size)
        verify(delegate).fetchProjectFiles(any(), eq(1L), eq(10), eq(1024))
    }

    @Test
    fun `fetchProjectFiles routes through MCP when host matches`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg())
        whenever(routed.fetchProjectFiles(any(), any(), any(), any())).thenReturn(emptyList())
        adapter.fetchProjectFiles(cfg(), 1L, 10, 1024)
        verify(routed).fetchProjectFiles(any(), eq(1L), eq(10), eq(1024))
    }

    @Test
    fun `fetchProjectFiles delegates when MCP host does not match`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg())
        whenever(delegate.fetchProjectFiles(any(), any(), any(), any())).thenReturn(emptyList())
        adapter.fetchProjectFiles(cfg("https://other.example.com"), 1L, 10, 1024)
        verify(delegate).fetchProjectFiles(any(), eq(1L), eq(10), eq(1024))
    }

    @Test
    fun `listProjectFilePaths delegates when no MCP active`() {
        whenever(delegate.listProjectFilePaths(any(), any())).thenReturn(emptyList())
        val result = adapter.listProjectFilePaths(cfg(), 1L)
        assertEquals(0, result.size)
        verify(delegate).listProjectFilePaths(any(), eq(1L))
    }

    @Test
    fun `listProjectFilePaths routes through MCP when host matches`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg())
        whenever(routed.listProjectFilePaths(any(), any())).thenReturn(emptyList())
        adapter.listProjectFilePaths(cfg(), 1L)
        verify(routed).listProjectFilePaths(any(), eq(1L))
    }

    @Test
    fun `listProjectFilePaths delegates when MCP host does not match`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg())
        whenever(delegate.listProjectFilePaths(any(), any())).thenReturn(emptyList())
        adapter.listProjectFilePaths(cfg("https://other.example.com"), 1L)
        verify(delegate).listProjectFilePaths(any(), eq(1L))
    }

    @Test
    fun `fetchProjectFilesForPath delegates when no MCP active`() {
        whenever(delegate.fetchProjectFilesForPath(any(), any(), any(), any(), any())).thenReturn(emptyList())
        val result = adapter.fetchProjectFilesForPath(cfg(), 1L, "src", 10, 1024)
        assertEquals(0, result.size)
        verify(delegate).fetchProjectFilesForPath(any(), eq(1L), eq("src"), eq(10), eq(1024))
    }

    @Test
    fun `fetchProjectFilesForPath routes through MCP when host matches`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg())
        whenever(routed.fetchProjectFilesForPath(any(), any(), any(), any(), any())).thenReturn(emptyList())
        adapter.fetchProjectFilesForPath(cfg(), 1L, "src", 10, 1024)
        verify(routed).fetchProjectFilesForPath(any(), eq(1L), eq("src"), eq(10), eq(1024))
    }

    @Test
    fun `fetchProjectFilesForPath delegates when MCP host does not match`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg())
        whenever(delegate.fetchProjectFilesForPath(any(), any(), any(), any(), any())).thenReturn(emptyList())
        adapter.fetchProjectFilesForPath(cfg("https://other.example.com"), 1L, "src", 10, 1024)
        verify(delegate).fetchProjectFilesForPath(any(), eq(1L), eq("src"), eq(10), eq(1024))
    }

    @Test
    fun `searchKeyword delegates when no MCP active`() {
        whenever(delegate.searchKeyword(any(), any(), any(), anyOrNull(), any())).thenReturn(emptyList())
        val result = adapter.searchKeyword(cfg(), "kw", 1L, null, emptyList())
        assertEquals(0, result.size)
        verify(delegate).searchKeyword(any(), eq("kw"), eq(1L), anyOrNull(), eq(emptyList()))
    }

    @Test
    fun `searchKeyword routes through MCP when host matches`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg())
        whenever(routed.searchKeyword(any(), any(), any(), anyOrNull(), any())).thenReturn(emptyList())
        adapter.searchKeyword(cfg(), "kw", 1L, null, emptyList())
        verify(routed).searchKeyword(any(), eq("kw"), eq(1L), anyOrNull(), eq(emptyList()))
    }

    @Test
    fun `searchKeyword delegates when MCP host does not match`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg())
        whenever(delegate.searchKeyword(any(), any(), any(), anyOrNull(), any())).thenReturn(emptyList())
        adapter.searchKeyword(cfg("https://other.example.com"), "kw", 1L, null, emptyList())
        verify(delegate).searchKeyword(any(), eq("kw"), eq(1L), anyOrNull(), eq(emptyList()))
    }

    @Test
    fun `searchKeyword returns result list unchanged`() {
        val results = listOf<SearchResult>()
        whenever(delegate.searchKeyword(any(), any(), any(), anyOrNull(), any())).thenReturn(results)
        val result = adapter.searchKeyword(cfg(), "kw", 1L, null, emptyList())
        assertSame(results, result)
    }

    @Test
    fun `listAccessibleProjects delegates when no MCP active`() {
        whenever(delegate.listAccessibleProjects(any())).thenReturn(emptyList())
        val result = adapter.listAccessibleProjects(cfg())
        assertEquals(0, result.size)
        verify(delegate).listAccessibleProjects(any())
    }

    @Test
    fun `listAccessibleProjects routes through MCP when host matches`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg())
        whenever(routed.listAccessibleProjects(any())).thenReturn(emptyList())
        adapter.listAccessibleProjects(cfg())
        verify(routed).listAccessibleProjects(any())
    }

    @Test
    fun `listAccessibleProjects delegates when MCP host does not match`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg())
        whenever(delegate.listAccessibleProjects(any())).thenReturn(emptyList())
        adapter.listAccessibleProjects(cfg("https://other.example.com"))
        verify(delegate).listAccessibleProjects(any())
    }

    @Test
    fun `resolvePort uses routed when MCP gitlabBaseUrl scheme and authority match`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg())
        whenever(routed.searchKeyword(any(), any(), any(), anyOrNull(), any())).thenReturn(emptyList())
        adapter.searchKeyword(cfg("https://gitlab.example.com"), "kw", 1L, null, emptyList())
        verify(routed).searchKeyword(any(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun `resolvePort is case-insensitive when comparing hosts`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg())
        whenever(routed.searchKeyword(any(), any(), any(), anyOrNull(), any())).thenReturn(emptyList())
        adapter.searchKeyword(cfg("https://GITLAB.EXAMPLE.COM"), "kw", 1L, null, emptyList())
        verify(routed).searchKeyword(any(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun `resolvePort with non-matching scheme falls back to delegate`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg())
        whenever(delegate.searchKeyword(any(), any(), any(), anyOrNull(), any())).thenReturn(emptyList())
        adapter.searchKeyword(cfg("http://gitlab.example.com"), "kw", 1L, null, emptyList())
        verify(delegate).searchKeyword(any(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun `resolvePort handles invalid MCP gitlabBaseUrl by fallback string comparison`() {
        val badCfg = mcpCfg(gitlabBaseUrl = "not-a-url")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(badCfg)
        whenever(delegate.searchKeyword(any(), any(), any(), anyOrNull(), any())).thenReturn(emptyList())
        adapter.searchKeyword(cfg("https://gitlab.example.com"), "kw", 1L, null, emptyList())
        verify(delegate).searchKeyword(any(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun `resolvePort handles invalid MCP gitlabBaseUrl by matching trimmed string`() {
        val badCfg = mcpCfg(gitlabBaseUrl = "https://gitlab.example.com")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(badCfg)
        whenever(routed.searchKeyword(any(), any(), any(), anyOrNull(), any())).thenReturn(emptyList())
        adapter.searchKeyword(cfg("https://gitlab.example.com"), "kw", 1L, null, emptyList())
        verify(routed).searchKeyword(any(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun `resolvePort handles MCP config with trailing slash in gitlabBaseUrl`() {
        val trailingSlash = mcpCfg(gitlabBaseUrl = "https://gitlab.example.com/")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(trailingSlash)
        whenever(routed.searchKeyword(any(), any(), any(), anyOrNull(), any())).thenReturn(emptyList())
        adapter.searchKeyword(cfg("https://gitlab.example.com"), "kw", 1L, null, emptyList())
        verify(routed).searchKeyword(any(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun `resolvePort handles config with trailing slash`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg())
        whenever(routed.searchKeyword(any(), any(), any(), anyOrNull(), any())).thenReturn(emptyList())
        adapter.searchKeyword(cfg("https://gitlab.example.com/"), "kw", 1L, null, emptyList())
        verify(routed).searchKeyword(any(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun `listAccessibleProjects returns routed result unchanged`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg())
        val expected = listOf<GitlabProjectInfo>()
        whenever(routed.listAccessibleProjects(any())).thenReturn(expected)
        val result = adapter.listAccessibleProjects(cfg())
        assertSame(expected, result)
    }

    @Test
    fun `resolvePort falls back to raw trimmed URL when URI parsing throws`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(
            mcpCfg(gitlabBaseUrl = "not a valid url"),
        )
        whenever(routed.searchKeyword(any(), any(), any(), anyOrNull(), any())).thenReturn(emptyList())
        adapter.searchKeyword(cfg("not a valid url"), "kw", 1L, null, emptyList())
        verify(routed).searchKeyword(any(), any(), any(), anyOrNull(), any())
    }
}
