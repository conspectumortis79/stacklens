package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.search.ConfluenceSearchPort
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.SearchResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class McpAwareConfluenceSearchAdapterTest {
    private val mcpRepo: McpServerConfigPort = mock()
    private val gateway: McpGatewayPort = mock()
    private val delegate: ConfluenceSearchPort = mock()
    private val mapper = ObjectMapper()
    private val adapter = McpAwareConfluenceSearchAdapter(mcpRepo, gateway, mapper, delegate)

    @Test
    fun `delegate is called when no MCP server is active`() {
        val config =
            ConfluenceConfig(
                name = "test",
                baseUrl = "https://conf.example.com",
                username = "u",
                apiToken = "t",
            )
        val expected = listOf<SearchResult>()
        whenever(delegate.searchKeyword(config, "kw", listOf("site"), 1L, 0)).thenReturn(expected)
        val result = adapter.searchKeyword(config, "kw", listOf("site"), 1L, 0)
        assertSame(expected, result)
        verify(delegate).searchKeyword(config, "kw", listOf("site"), 1L, 0)
    }

    @Test
    fun `delegate is called when MCP baseUrl does not match config baseUrl`() {
        val config =
            ConfluenceConfig(
                name = "test",
                baseUrl = "https://conf.example.com",
                username = "u",
                apiToken = "t",
            )
        whenever(delegate.searchKeyword(config, "kw", emptyList(), 0L, 0)).thenReturn(emptyList())
        adapter.searchKeyword(config, "kw", emptyList(), 0L, 0)
        verify(delegate).searchKeyword(config, "kw", emptyList(), 0L, 0)
    }

    @Test
    fun `delegate is called when offset is greater than zero`() {
        val config =
            ConfluenceConfig(
                name = "test",
                baseUrl = "https://conf.example.com",
                username = "u",
                apiToken = "t",
            )
        whenever(delegate.searchKeyword(config, "kw", emptyList(), 0L, 5)).thenReturn(emptyList())
        adapter.searchKeyword(config, "kw", emptyList(), 0L, 5)
        verify(delegate).searchKeyword(config, "kw", emptyList(), 0L, 5)
    }

    @Test
    fun `search passes through websiteFilters and sessionId to delegate`() {
        val config =
            ConfluenceConfig(
                name = "test",
                baseUrl = "https://conf.example.com",
                username = "u",
                apiToken = "t",
            )
        whenever(
            delegate.searchKeyword(
                config,
                "refactor",
                listOf("site1", "site2"),
                42L,
                0,
            ),
        ).thenReturn(emptyList())
        adapter.searchKeyword(config, "refactor", listOf("site1", "site2"), 42L, 0)
        verify(delegate).searchKeyword(config, "refactor", listOf("site1", "site2"), 42L, 0)
    }

    @Test
    fun `search result from delegate is returned unchanged`() {
        val config =
            ConfluenceConfig(
                name = "test",
                baseUrl = "https://conf.example.com",
                username = "u",
                apiToken = "t",
            )
        val results =
            listOf(
                SearchResult(
                    sessionId = 1L,
                    keyword = "kw",
                    sourceType = com.stacklens.domain.enums.SourceType.CONFLUENCE,
                    repoName = "T",
                    repoUrl = "https://example.com/p/1",
                    codeSnippet = "S",
                ),
            )
        whenever(delegate.searchKeyword(config, "kw", emptyList(), 0L, 0)).thenReturn(results)
        val result = adapter.searchKeyword(config, "kw", emptyList(), 0L, 0)
        assertEquals(1, result.size)
        assertEquals("T", result[0].repoName)
        assertEquals("https://example.com/p/1", result[0].repoUrl)
    }

    @Test
    fun `delegate is never invoked through the MCP gateway when no MCP active`() {
        val config =
            ConfluenceConfig(
                name = "test",
                baseUrl = "https://conf.example.com",
                username = "u",
                apiToken = "t",
            )
        whenever(delegate.searchKeyword(config, "kw", emptyList(), 0L, 0)).thenReturn(emptyList())
        adapter.searchKeyword(config, "kw", emptyList(), 0L, 0)
        verify(gateway, never()).ping(anyOrNull())
    }

    @Test
    fun `delegate is called when offset is greater than zero and MCP is active`() {
        val mcpCfg =
            com.stacklens.domain.model.McpServerConfig(
                name = "mcp",
                baseUrl = "http://localhost",
                active = true,
                routeConfluence = true,
                confluenceBaseUrl = "https://conf.example.com",
            )
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        val config =
            ConfluenceConfig(
                name = "test",
                baseUrl = "https://conf.example.com",
                username = "u",
                apiToken = "t",
            )
        whenever(delegate.searchKeyword(config, "kw", emptyList(), 1L, 5)).thenReturn(emptyList())
        val result = adapter.searchKeyword(config, "kw", emptyList(), 1L, 5)
        verify(delegate).searchKeyword(config, "kw", emptyList(), 1L, 5)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `delegate is invoked when MCP baseUrl does not match config baseUrl`() {
        val mcpCfg =
            com.stacklens.domain.model.McpServerConfig(
                name = "mcp",
                baseUrl = "http://localhost",
                active = true,
                routeConfluence = true,
                confluenceBaseUrl = "https://other-confluence.example.com",
            )
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        val config =
            ConfluenceConfig(
                name = "test",
                baseUrl = "https://conf.example.com",
                username = "u",
                apiToken = "t",
            )
        whenever(delegate.searchKeyword(config, "kw", emptyList(), 1L, 0)).thenReturn(emptyList())
        adapter.searchKeyword(config, "kw", emptyList(), 1L, 0)
        verify(delegate).searchKeyword(config, "kw", emptyList(), 1L, 0)
    }

    @Test
    fun `delegate is invoked when gateway returns an error result`() {
        val mcpCfg =
            com.stacklens.domain.model.McpServerConfig(
                name = "mcp",
                baseUrl = "http://localhost",
                active = true,
                routeConfluence = true,
                confluenceBaseUrl = "https://conf.example.com",
            )
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        val config =
            ConfluenceConfig(
                name = "test",
                baseUrl = "https://conf.example.com",
                username = "u",
                apiToken = "t",
            )
        whenever(
            gateway.callTool(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()),
        ).thenReturn(
            com.stacklens.application.port.out
                .McpToolResult(text = "tool error", isError = true),
        )
        whenever(delegate.searchKeyword(config, "kw", emptyList(), 1L, 0)).thenReturn(emptyList())
        val result = adapter.searchKeyword(config, "kw", emptyList(), 1L, 0)
        verify(delegate).searchKeyword(config, "kw", emptyList(), 1L, 0)
        assertTrue(result.isEmpty())
    }
}
