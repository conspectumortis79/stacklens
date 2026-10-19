package com.stacklens.application.port.out

import com.stacklens.domain.model.McpServerConfig

interface McpGatewayPort {
    fun listTools(config: McpServerConfig): List<McpToolDescriptor>

    fun callTool(
        config: McpServerConfig,
        tool: String,
        args: Map<String, Any?>,
    ): McpToolResult

    fun ping(config: McpServerConfig): McpPingResult
}

data class McpToolDescriptor(
    val name: String,
    val description: String = "",
)

data class McpToolResult(
    val text: String,
    val isError: Boolean = false,
)

data class McpPingResult(
    val ok: Boolean,
    val message: String,
    val toolCount: Int = 0,
)
