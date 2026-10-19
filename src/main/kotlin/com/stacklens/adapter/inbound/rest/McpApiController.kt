package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.mcp.McpManagementUseCase
import com.stacklens.application.port.inbound.mcp.McpToolCallRequest
import com.stacklens.application.port.inbound.platform.I18nUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/mcp")
class McpApiController(
    private val mcpManagement: McpManagementUseCase,
    private val i18n: I18nUseCase,
) {
    private fun tr(
        key: String,
        vararg args: Any,
    ): String = i18n.t(key, *args)

    @GetMapping("/tools")
    fun listTools(
        @RequestParam(required = false) mcpServerId: Long?,
    ): ResponseEntity<Map<String, Any>> {
        val result =
            mcpManagement.listTools(mcpServerId)
                ?: return ResponseEntity.badRequest().body(mapOf("error" to tr("mcp.be_no_active_server")))
        return ResponseEntity.ok(
            mapOf(
                "mcpServerId" to result.mcpServerId,
                "mcpServerName" to result.mcpServerName,
                "tools" to result.tools.map { mapOf("name" to it.name, "description" to it.description) },
            ),
        )
    }

    @PostMapping("/call")
    fun call(
        @RequestBody request: McpApiDtoRequest,
    ): ResponseEntity<McpToolCallResponse> {
        if (request.tool.isBlank()) {
            return ResponseEntity.badRequest().body(McpToolCallResponse(ok = false, text = "tool is required", tool = ""))
        }
        val ucReq = McpToolCallRequest(mcpServerId = request.mcpServerId, tool = request.tool, args = request.args)
        val result = mcpManagement.callTool(ucReq)
        if (!result.ok && result.text == tr("mcp.be_no_active_server")) {
            return ResponseEntity.badRequest().body(McpToolCallResponse(ok = false, text = result.text, tool = request.tool))
        }
        return ResponseEntity.ok(McpToolCallResponse(ok = result.ok, text = result.text, tool = result.tool))
    }

    @GetMapping("/status")
    fun status(): ResponseEntity<Map<String, Any?>> {
        val s = mcpManagement.getStatus()
        return ResponseEntity.ok(
            mapOf(
                "active" to s.active,
                "mcpServerName" to s.mcpServerName,
                "routes" to s.routes,
                "processRunning" to s.processRunning,
            ),
        )
    }

    data class McpApiDtoRequest(
        val tool: String = "",
        val args: Map<String, Any?> = emptyMap(),
        val mcpServerId: Long? = null,
    )
}
