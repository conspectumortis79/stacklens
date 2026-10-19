package com.stacklens.application.service

import com.stacklens.application.port.inbound.chat.ChatRequest
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolDescriptor
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.McpServerConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KiChatMcpPreflightTest {
    private val mcpConfigs: McpServerConfigPort = mock()
    private val mcpGateway: McpGatewayPort = mock()
    private val jsonCodec: JsonCodecPort = mock()
    private val copilot: CopilotPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val sut =
        KiChatMcpPreflight(
            mcpConfigs = mcpConfigs,
            mcpGateway = mcpGateway,
            jsonCodec = jsonCodec,
            copilot = copilot,
            promptLoader = promptLoader,
        )

    @Test
    fun `run returns empty string when no active mcp config exists`() {
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenReturn(null)
        assertThat(sut.run("gpt-4", ChatRequest(tabId = "t", userMessage = "hi"))).isEmpty()
    }

    @Test
    fun `run returns empty string when tool list is empty`() {
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenReturn(activeConfig())
        whenever(mcpGateway.listTools(any())).thenReturn(emptyList())

        assertThat(sut.run("gpt-4", ChatRequest(tabId = "t", userMessage = "hi"))).isEmpty()
    }

    @Test
    fun `run returns empty string when the planned call list is empty`() {
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenReturn(activeConfig())
        whenever(mcpGateway.listTools(any())).thenReturn(listOf(McpToolDescriptor("x", "x")))
        whenever(promptLoader.get(any(), any(), anyOrNull())).thenReturn("plan")
        whenever(copilot.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn("[]")

        assertThat(sut.run("gpt-4", ChatRequest(tabId = "t", userMessage = "hi"))).isEmpty()
    }

    @Test
    fun `run invokes the gateway tool and renders the result`() {
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenReturn(activeConfig())
        whenever(mcpGateway.listTools(any()))
            .thenReturn(listOf(McpToolDescriptor("get_issue", "Fetch issue")))
        whenever(promptLoader.get(any(), any(), anyOrNull())).thenReturn("plan")
        whenever(copilot.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn(
            """[{"tool":"get_issue","args":{"id":"42"}}]""",
        )
        whenever(jsonCodec.parseArrayOrEmpty(any()))
            .thenReturn(listOf(mapOf("tool" to "get_issue", "args" to mapOf("id" to "42"))))
        whenever(mcpGateway.callTool(any(), any(), any()))
            .thenReturn(McpToolResult("Issue #42"))

        val out = sut.run("gpt-4", ChatRequest(tabId = "t", userMessage = "show 42"))

        assertThat(out).contains("### Tool: get_issue")
        assertThat(out).contains("Issue #42")
    }

    private fun activeConfig(): McpServerConfig = McpServerConfig(id = 1L, name = "test", baseUrl = "http://localhost:7456", active = true)
}
