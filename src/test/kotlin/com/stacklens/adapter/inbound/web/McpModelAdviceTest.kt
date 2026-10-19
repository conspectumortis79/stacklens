package com.stacklens.adapter.inbound.web

import com.stacklens.application.port.inbound.mcp.McpServerManagementUseCase
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

class McpModelAdviceTest {
    @Test
    fun `mcpServerConfigs returns list from repo`() {
        val cfg =
            McpServerConfig(
                name = "MCP",
                baseUrl = "http://mcp",
            )
        val repo =
            mock<McpServerManagementUseCase> {
                on { allServerConfigs() } doReturn listOf(cfg)
            }
        val advice = McpModelAdvice(repo)
        assertEquals(listOf(cfg), advice.mcpServerConfigs())
    }

    @Test
    fun `mcpServerConfigs returns empty list when repo is empty`() {
        val repo =
            mock<McpServerManagementUseCase> {
                on { allServerConfigs() } doReturn emptyList()
            }
        val advice = McpModelAdvice(repo)
        assertTrue(advice.mcpServerConfigs().isEmpty())
    }

    @Test
    fun `mcpServerConfigs returns empty list when repo throws`() {
        val repo =
            mock<McpServerManagementUseCase> {
                on { allServerConfigs() } doThrow RuntimeException("db down")
            }
        val advice = McpModelAdvice(repo)
        assertTrue(advice.mcpServerConfigs().isEmpty())
    }

    @Test
    fun `mcpServerConfigs survives chained calls`() {
        val repo = mock<McpServerManagementUseCase>()
        repo.stub {
            on { allServerConfigs() } doReturn emptyList() doReturn listOf(McpServerConfig(name = "x", baseUrl = "u"))
        }
        val advice = McpModelAdvice(repo)
        assertTrue(advice.mcpServerConfigs().isEmpty())
        assertEquals(1, advice.mcpServerConfigs().size)
    }
}
