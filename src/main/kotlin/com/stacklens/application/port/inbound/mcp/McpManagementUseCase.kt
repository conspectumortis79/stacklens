package com.stacklens.application.port.inbound.mcp
import com.stacklens.domain.model.CircuitSnapshot

data class McpToolsResult(
    val mcpServerId: Long,
    val mcpServerName: String,
    val tools: List<McpToolSummary>,
)

data class McpToolSummary(
    val name: String,
    val description: String,
)

data class McpCallResult(
    val ok: Boolean,
    val text: String,
    val tool: String,
)

data class McpToolCallRequest(
    val mcpServerId: Long? = null,
    val tool: String = "",
    val args: Map<String, Any?> = emptyMap(),
)

data class McpStatusResult(
    val active: Boolean,
    val mcpServerName: String?,
    val routes: Map<String, Boolean>,
    val processRunning: Boolean,
    val circuits: Map<String, CircuitSnapshot> = emptyMap(),
)

interface McpManagementUseCase {
    fun listTools(mcpServerId: Long?): McpToolsResult?

    fun callTool(request: McpToolCallRequest): McpCallResult

    fun getStatus(): McpStatusResult
}
