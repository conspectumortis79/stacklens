package com.stacklens.application.port.inbound.mcp

import com.stacklens.domain.model.McpServerConfig

interface McpServerCatalog {
    fun allServerConfigs(): List<McpServerConfig>
}
