package com.stacklens.adapter.inbound.web

import com.stacklens.application.port.inbound.mcp.McpServerCatalog
import com.stacklens.domain.model.McpServerConfig
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

@ControllerAdvice
class McpModelAdvice(
    private val mcpServerManagement: McpServerCatalog,
) {
    @ModelAttribute("mcpServerConfigs")
    fun mcpServerConfigs(): List<McpServerConfig> = runCatching { mcpServerManagement.allServerConfigs() }.getOrDefault(emptyList())
}
